package org.gradle.profiler.studio;

import com.google.common.base.Joiner;
import org.gradle.profiler.*;
import org.gradle.profiler.BuildAction.BuildActionResult;
import org.gradle.profiler.client.protocol.*;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.client.protocol.messages.SyncCompleted;
import org.gradle.profiler.studio.plugin.StudioPluginInstaller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.SYNC;

public class StudioGradleClient implements GradleClient {
    private final Server agentServer;
    private final Server pluginServer;
    private final CommandExec.RunHandle studioProcess;
    private final ServerConnection agentConnection;
    private final ServerConnection pluginAgentConnection;

    public StudioGradleClient(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
        if (!OperatingSystem.isMacOS()) {
            throw new IllegalArgumentException("Support for Android studio is currently only implemented on macOS.");
        }
        Path studioInstallDir = invocationSettings.getStudioInstallDir().toPath();
        Logging.startOperation("Starting Android Studio at " + studioInstallDir);

        pluginServer = new Server("plugin");
        agentServer = new Server("agent");
        LaunchConfiguration launchConfiguration = new LauncherConfigurationParser().calculate(studioInstallDir, Integer.toString(pluginServer.getPort()));
        System.out.println();
        System.out.println("* Java command: " + launchConfiguration.getJavaCommand());
        System.out.println("* Classpath:");
        for (Path entry : launchConfiguration.getClassPath()) {
            System.out.println("  " + entry);
        }
        System.out.println("* System properties:");
        for (Map.Entry<String, String> entry : launchConfiguration.getSystemProperties().entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println("* Main class: " + launchConfiguration.getMainClass());

        studioProcess = startStudio(launchConfiguration, studioInstallDir, invocationSettings, agentServer);
        pluginAgentConnection = pluginServer.waitForIncoming(Duration.ofMinutes(1));
        agentConnection = agentServer.waitForIncoming(Duration.ofMinutes(1));
        agentConnection.send(new ConnectionParameters(buildConfiguration.getGradleHome()));
    }

    private CommandExec.RunHandle startStudio(LaunchConfiguration launchConfiguration, Path studioInstallDir, InvocationSettings invocationSettings, Server server) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(launchConfiguration.getJavaCommand().toString());
        commandLine.add("-cp");
        commandLine.add(Joiner.on(File.pathSeparator).join(launchConfiguration.getClassPath()));
        for (Map.Entry<String, String> systemProperty : launchConfiguration.getSystemProperties().entrySet()) {
            commandLine.add("-D" + systemProperty.getKey() + "=" + systemProperty.getValue());
        }
        commandLine.add("-javaagent:" + launchConfiguration.getAgentJar() + "=" + server.getPort() + "," + launchConfiguration.getSupportJar());
        commandLine.add("--add-exports");
        commandLine.add("java.base/jdk.internal.misc=ALL-UNNAMED");
        commandLine.add("-Xbootclasspath/a:" + Joiner.on(File.pathSeparator).join(launchConfiguration.getSharedJars()));
        commandLine.add(launchConfiguration.getMainClass());
        commandLine.add(invocationSettings.getProjectDir().getAbsolutePath());
        System.out.println("* Android Studio logs can be found at: " + Paths.get(launchConfiguration.getStudioPluginsDir().toString(), "idea.log"));
        System.out.println("* Using command line: " + commandLine);

        new StudioPluginInstaller().installPlugin(launchConfiguration);
        return new CommandExec().inDir(studioInstallDir.toFile()).start(commandLine);
    }

    @Override
    public void close() throws IOException {
        System.out.println("* STOPPING ANDROID STUDIO....");
        pluginAgentConnection.send(new StudioRequest(EXIT));
        studioProcess.waitForSuccess();
        System.out.println("* Android Studio stopped.");
        pluginServer.close();
        agentServer.close();
    }

    public BuildActionResult sync(List<String> gradleArgs, List<String> jvmArgs) {
        System.out.println("* PLEASE WAIT TO RUN SYNC IN ANDROID STUDIO...");
        pluginAgentConnection.send(new StudioRequest(SYNC));
        System.out.println("* Sent sync request");
        // Use a long time out because it can take quite some time
        // between the tapi action completing and studio finishing the sync
        agentConnection.receiveSyncStarted(Duration.ofMinutes(10));
        agentConnection.send(new SyncParameters(gradleArgs, jvmArgs));
        System.out.println("* Sync has started, waiting for it to complete...");
        SyncCompleted agentCompleted = agentConnection.receiveSyncCompleted(Duration.ofHours(1));
        System.out.println("* Gradle Sync has completed in: " + agentCompleted.getDurationMillis() + "ms");
        StudioSyncRequestCompleted syncRequestCompleted = pluginAgentConnection.receiveSyncRequestCompleted(Duration.ofMinutes(20));
        System.out.println("* Full Sync has completed in: " + syncRequestCompleted.getDurationMillis() + "ms and it " + syncRequestCompleted.getResult().name().toLowerCase());
        return BuildActionResult.of(
            Duration.ofMillis(syncRequestCompleted.getDurationMillis()),
            Duration.ofMillis(agentCompleted.getDurationMillis()),
            Duration.ofMillis(syncRequestCompleted.getDurationMillis() - agentCompleted.getDurationMillis())
        );
    }
}

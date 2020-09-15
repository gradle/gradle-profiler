package org.gradle.profiler.studio;

import com.google.common.base.Joiner;
import org.gradle.profiler.*;
import org.gradle.profiler.client.protocol.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StudioGradleClient implements GradleClient {
    private final Server server;
    private final CommandExec.RunHandle studioProcess;
    private final ServerConnection agentConnection;
    private boolean hasRun;

    public StudioGradleClient(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
        Path studioInstallDir = invocationSettings.getStudioInstallDir().toPath();
        Logging.startOperation("Starting Android Studio at " + studioInstallDir);

        LaunchConfiguration launchConfiguration = new LauncherConfigurationParser().calculate(studioInstallDir);
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

        server = new Server("agent");
        studioProcess = startStudio(launchConfiguration, studioInstallDir, invocationSettings, server);
        agentConnection = server.waitForIncoming(Duration.ofMinutes(1));
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
        commandLine.add(invocationSettings.getProjectDir().toString());
        System.out.println("* Using command line: " + commandLine);

        return new CommandExec().inDir(studioInstallDir.toFile()).start(commandLine);
    }

    @Override
    public void close() throws IOException {
        System.out.println("* PLEASE STOP ANDROID STUDIO....");
        studioProcess.waitForSuccess();
        System.out.println("* Android Studio stopped.");

        server.close();
    }

    public Duration sync(List<String> gradleArgs, List<String> jvmArgs) {
        if (!hasRun) {
            System.out.println("* PLEASE RUN SYNC IN ANDROID STUDIO (once it has finished starting up)....");
            hasRun = true;
        } else {
            System.out.println("* PLEASE RUN SYNC IN ANDROID STUDIO....");
        }

        // Use a long time out because it can take quite some time between the tapi action completing and studio finishing the sync
        SyncStarted started = agentConnection.receiveSyncStarted(Duration.ofMinutes(10));
        agentConnection.send(new SyncParameters(gradleArgs, jvmArgs));
        System.out.println("* Sync has started");
        SyncCompleted completed = agentConnection.receiveSyncCompeted(Duration.ofHours(1));
        System.out.println("* Sync has completed");
        return Duration.ofMillis(completed.getDurationMillis());
    }
}

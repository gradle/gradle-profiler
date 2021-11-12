package org.gradle.profiler.studio;

import org.gradle.profiler.BuildAction.BuildActionResult;
import org.gradle.profiler.CommandExec.RunHandle;
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Logging;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.client.protocol.Server;
import org.gradle.profiler.client.protocol.ServerConnection;
import org.gradle.profiler.client.protocol.messages.GradleInvocationCompleted;
import org.gradle.profiler.client.protocol.messages.GradleInvocationParameters;
import org.gradle.profiler.client.protocol.messages.StudioAgentConnectionParameters;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.studio.tools.StudioLauncher;
import org.gradle.profiler.studio.tools.StudioPluginInstaller;
import org.gradle.profiler.studio.tools.StudioSandboxCreator;
import org.gradle.profiler.studio.tools.StudioSandboxCreator.StudioSandbox;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT_IDE;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.SYNC;

public class StudioGradleClient implements GradleClient {

    private static final Duration PLUGIN_CONNECT_TIMEOUT = Duration.ofSeconds(12);
    private static final Duration PROJECT_OPENED_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration AGENT_CONNECT_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration SYNC_STARTED_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration GRADLE_INVOCATION_COMPLETED_TIMEOUT = Duration.ofMinutes(60);
    private static final Duration SYNC_REQUEST_COMPLETED_TIMEOUT = Duration.ofMinutes(60);
    private static final long STUDIO_EXIT_TIMEOUT_SECONDS = 60;

    private final Server studioAgentServer;
    private final Server studioPluginServer;
    private final RunHandle studioProcess;
    private ServerConnection studioAgentConnection;
    private ServerConnection studioPluginConnection;
    private final StudioPluginInstaller studioPluginInstaller;

    public StudioGradleClient(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
        if (!OperatingSystem.isMacOS()) {
            throw new IllegalArgumentException("Support for Android studio is currently only implemented on macOS.");
        }
        Path studioInstallDir = invocationSettings.getStudioInstallDir().toPath();
        Optional<File> studioSandboxDir = invocationSettings.getStudioSandboxDir();
        Logging.startOperation("Starting Android Studio at " + studioInstallDir);
        studioPluginServer = new Server("plugin");
        studioAgentServer = new Server("agent");
        StudioSandbox sandbox = StudioSandboxCreator.createSandbox(studioSandboxDir.map(File::toPath).orElse(null));
        LaunchConfiguration launchConfiguration = new LauncherConfigurationParser(studioInstallDir, sandbox)
            .installStudioPlugin(studioPluginServer.getPort())
            .installStudioAgent(studioAgentServer.getPort())
            .calculate();

        studioPluginInstaller = new StudioPluginInstaller(launchConfiguration.getStudioPluginsDir());
        studioPluginInstaller.installPlugin(launchConfiguration.getStudioPluginJars());
        studioProcess = StudioLauncher.launchStudio(launchConfiguration, invocationSettings.getProjectDir());
        studioPluginConnection = waitOnSuccessfulPluginConnection(studioProcess);
        studioAgentConnection = studioAgentServer.waitForIncoming(AGENT_CONNECT_TIMEOUT);
        studioAgentConnection.send(new StudioAgentConnectionParameters(buildConfiguration.getGradleHome()));
    }

    private ServerConnection waitOnSuccessfulPluginConnection(RunHandle runHandle) {
        try {
            return studioPluginServer.waitForIncoming(PLUGIN_CONNECT_TIMEOUT);
        } catch (Exception e) {
            System.err.println("\n* ERROR\n" +
                "* Could not connect to Android Studio process started by the gradle-profiler.\n" +
                "* This might indicate that you are already running an Android Studio process in the same sandbox.\n" +
                "* Stop Android Studio manually in the used sandbox or use a different sandbox with --studio-sandbox-dir to isolate the process.\n");
            runHandle.kill();
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public BuildActionResult sync(List<String> gradleArgs, List<String> jvmArgs) {
        System.out.println("* Running sync in Android Studio...");
        studioPluginConnection.send(new StudioRequest(SYNC));
        System.out.println("* Sent sync request");
        // Use a long time out because it can take quite some time
        // between the tapi action completing and studio finishing the sync
        studioAgentConnection.receiveSyncStarted(SYNC_STARTED_TIMEOUT);
        studioAgentConnection.send(new GradleInvocationParameters(gradleArgs, jvmArgs));
        System.out.println("* Sync has started, waiting for it to complete...");
        GradleInvocationCompleted agentCompleted = studioAgentConnection.receiveGradleInvocationCompleted(GRADLE_INVOCATION_COMPLETED_TIMEOUT);
        System.out.println("* Gradle invocation has completed in: " + agentCompleted.getDurationMillis() + "ms");
        StudioSyncRequestCompleted syncRequestCompleted = studioPluginConnection.receiveSyncRequestCompleted(SYNC_REQUEST_COMPLETED_TIMEOUT);
        System.out.println("* Full sync has completed in: " + syncRequestCompleted.getDurationMillis() + "ms and it " + syncRequestCompleted.getResult().name().toLowerCase());
        return BuildActionResult.withIdeTimings(
            Duration.ofMillis(syncRequestCompleted.getDurationMillis()),
            Duration.ofMillis(agentCompleted.getDurationMillis()),
            Duration.ofMillis(syncRequestCompleted.getDurationMillis() - agentCompleted.getDurationMillis())
        );
    }


    @Override
    public void close() {
        try (Server studioPluginServer = this.studioPluginServer;
             Server studioAgentServer = this.studioAgentServer;
             Closeable uninstallPlugin = studioPluginInstaller::uninstallPlugin) {
            System.out.println("* Stopping Android Studio....");
            studioPluginConnection.send(new StudioRequest(EXIT_IDE));
            studioProcess.waitForSuccess(STUDIO_EXIT_TIMEOUT_SECONDS, SECONDS);
            System.out.println("* Android Studio stopped.");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("* Android Studio did not finish successfully, you will have to close it manually.");
        }
    }
}

package org.gradle.profiler.studio;

import org.gradle.profiler.BuildAction.BuildActionResult;
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.client.protocol.messages.GradleInvocationCompleted;
import org.gradle.profiler.client.protocol.messages.GradleInvocationParameters;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.instrument.GradleInstrumentation;
import org.gradle.profiler.studio.process.StudioProcessController;
import org.gradle.profiler.studio.tools.StudioPluginInstaller;
import org.gradle.profiler.studio.tools.StudioSandboxCreator;
import org.gradle.profiler.studio.tools.StudioSandboxCreator.StudioSandbox;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.CLEANUP_CACHE;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT_IDE;
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.SYNC;

public class StudioGradleClient implements GradleClient {

    public enum CleanCacheMode {
        BEFORE_SCENARIO,
        BEFORE_BUILD,
        NEVER
    }

    private static final Duration SYNC_STARTED_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration CACHE_CLEANUP_COMPLETED_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration GRADLE_INVOCATION_COMPLETED_TIMEOUT = Duration.ofMinutes(60);
    private static final Duration SYNC_REQUEST_COMPLETED_TIMEOUT = Duration.ofMinutes(60);

    private final StudioProcessController processController;
    private final StudioPluginInstaller studioPluginInstaller;
    private final CleanCacheMode cleanCacheMode;
    private boolean isFirstRun;

    public StudioGradleClient(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, CleanCacheMode cleanCacheMode) {
        this.isFirstRun = true;
        this.cleanCacheMode = cleanCacheMode;
        Path studioInstallDir = invocationSettings.getStudioInstallDir().toPath();
        Optional<File> studioSandboxDir = invocationSettings.getStudioSandboxDir();
        StudioSandbox sandbox = StudioSandboxCreator.createSandbox(studioSandboxDir.map(File::toPath).orElse(null));
        Path protocolJar = GradleInstrumentation.unpackPlugin("client-protocol").toPath();
        Path studioPlugin = GradleInstrumentation.unpackPlugin("studio-plugin").toPath();
        this.studioPluginInstaller = new StudioPluginInstaller(sandbox.getPluginsDir());
        studioPluginInstaller.installPlugin(Arrays.asList(studioPlugin, protocolJar));
        this.processController = new StudioProcessController(studioInstallDir, sandbox, invocationSettings, buildConfiguration);
    }

    public BuildActionResult sync(List<String> gradleArgs, List<String> jvmArgs) {
        if (shouldCleanCache()) {
            processController.runAndWaitToStop((connections) -> {
                System.out.println("* Cleaning Android Studio cache, this will require a restart...");
                connections.getPluginConnection().send(new StudioRequest(CLEANUP_CACHE));
                connections.getPluginConnection().receiveCacheCleanupCompleted(CACHE_CLEANUP_COMPLETED_TIMEOUT);
                connections.getPluginConnection().send(new StudioRequest(EXIT_IDE));
            });
        }

        isFirstRun = false;
        return processController.run((connections) -> {
            System.out.println("* Running sync in Android Studio...");
            connections.getPluginConnection().send(new StudioRequest(SYNC));
            System.out.println("* Sent sync request");
            // Use a long time out because it can take quite some time
            // between the tapi action completing and studio finishing the sync
            connections.getAgentConnection().receiveSyncStarted(SYNC_STARTED_TIMEOUT);
            connections.getAgentConnection().send(new GradleInvocationParameters(gradleArgs, jvmArgs));
            System.out.println("* Sync has started, waiting for it to complete...");
            GradleInvocationCompleted agentCompleted = connections.getAgentConnection().receiveGradleInvocationCompleted(GRADLE_INVOCATION_COMPLETED_TIMEOUT);
            System.out.println("* Gradle invocation has completed in: " + agentCompleted.getDurationMillis() + "ms");
            StudioSyncRequestCompleted syncRequestCompleted = connections.getPluginConnection().receiveSyncRequestCompleted(SYNC_REQUEST_COMPLETED_TIMEOUT);
            System.out.println("* Full sync has completed in: " + syncRequestCompleted.getDurationMillis() + "ms and it " + syncRequestCompleted.getResult().name().toLowerCase());
            return BuildActionResult.withIdeTimings(
                Duration.ofMillis(syncRequestCompleted.getDurationMillis()),
                Duration.ofMillis(agentCompleted.getDurationMillis()),
                Duration.ofMillis(syncRequestCompleted.getDurationMillis() - agentCompleted.getDurationMillis())
            );
        });
    }

    private boolean shouldCleanCache() {
        return isFirstRun && cleanCacheMode == CleanCacheMode.BEFORE_SCENARIO
            || cleanCacheMode == CleanCacheMode.BEFORE_BUILD;
    }

    @Override
    public void close() {
        try {
            if (processController.isProcessRunning()) {
                processController.runAndWaitToStop((connections) -> {
                    System.out.println("* Stopping Android Studio....");
                    connections.getPluginConnection().send(new StudioRequest(EXIT_IDE));
                    System.out.println("* Android Studio stopped.");
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("* Android Studio did not finish successfully, you will have to close it manually.");
        } finally {
            studioPluginInstaller.uninstallPlugin();
        }
    }
}

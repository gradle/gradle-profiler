package org.gradle.profiler.studio;

import org.gradle.internal.Pair;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.client.protocol.ServerConnection;
import org.gradle.profiler.client.protocol.messages.*;
import org.gradle.profiler.gradle.DaemonControl;
import org.gradle.profiler.instrument.GradleInstrumentation;
import org.gradle.profiler.result.BuildActionResult;
import org.gradle.profiler.studio.invoker.StudioBuildActionResult;
import org.gradle.profiler.studio.invoker.StudioGradleScenarioDefinition;
import org.gradle.profiler.studio.invoker.StudioGradleScenarioDefinition.StudioGradleBuildConfiguration;
import org.gradle.profiler.studio.process.StudioProcess.StudioConnections;
import org.gradle.profiler.studio.process.StudioProcessController;
import org.gradle.profiler.studio.tools.StudioPluginInstaller;
import org.gradle.profiler.studio.tools.StudioSandboxCreator;
import org.gradle.profiler.studio.tools.StudioSandboxCreator.StudioSandbox;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.*;
import static org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult.FAILED;

public class StudioGradleClient implements GradleClient {

    public enum CleanCacheMode {
        BEFORE_SCENARIO,
        BEFORE_BUILD,
        NEVER
    }

    private static final Duration CACHE_CLEANUP_COMPLETED_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration GRADLE_INVOCATION_STARTED_TIMEOUT = Duration.ofMillis(100);
    private static final Duration GRADLE_INVOCATION_COMPLETED_TIMEOUT = Duration.ofMinutes(60);
    private static final Duration SYNC_REQUEST_COMPLETED_TIMEOUT = Duration.ofMinutes(90);

    private final StudioProcessController processController;
    private final StudioPluginInstaller studioPluginInstaller;
    private final CleanCacheMode cleanCacheMode;
    private final ExecutorService executor;
    private final StudioSandbox sandbox;
    private boolean isFirstRun;

    public StudioGradleClient(StudioGradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, CleanCacheMode cleanCacheMode) {
        this.isFirstRun = true;
        this.cleanCacheMode = cleanCacheMode;
        Path studioInstallDir = invocationSettings.getStudioInstallDir().toPath();
        Optional<File> studioSandboxDir = invocationSettings.getStudioSandboxDir();
        this.sandbox = StudioSandboxCreator.createSandbox(studioSandboxDir.map(File::toPath).orElse(null));
        Path protocolJar = GradleInstrumentation.unpackPlugin("client-protocol").toPath();
        Path studioPlugin = GradleInstrumentation.unpackPlugin("studio-plugin").toPath();
        this.studioPluginInstaller = new StudioPluginInstaller(sandbox.getPluginsDir());
        studioPluginInstaller.installPlugin(Arrays.asList(studioPlugin, protocolJar));
        this.processController = new StudioProcessController(studioInstallDir, sandbox, invocationSettings, buildConfiguration);
        this.executor = Executors.newSingleThreadExecutor();
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
            Pair<StudioSyncRequestCompleted, StudioBuildActionResult> pair = waitForSyncToFinish(connections, gradleArgs, jvmArgs);
            StudioSyncRequestCompleted syncRequestResult = pair.getLeft();
            StudioBuildActionResult durationResult = pair.getRight();
            System.out.printf("* Full Gradle execution time: %dms%n", durationResult.getGradleTotalExecutionTime().toMillis());
            System.out.printf("* Full IDE execution time: %dms%n", durationResult.getIdeExecutionTime().toMillis());
            System.out.printf("* Full sync has completed in: %dms and it %s%n", durationResult.getExecutionTime().toMillis(), syncRequestResult.getResult());
            maybeThrownExceptionOnSyncFailure(syncRequestResult);
            return durationResult;
        });
    }

    private void maybeThrownExceptionOnSyncFailure(StudioSyncRequestCompleted syncRequestResult) {
        if (syncRequestResult.getResult() == FAILED) {
            throw new IllegalStateException(String.format("Gradle sync has failed with error message: '%s'. Full Android Studio logs can be found in: '%s'.",
                syncRequestResult.getErrorMessage(),
                new File(sandbox.getLogsDir().toFile(), "idea.log").getAbsolutePath())
            );
        }
    }

    private Pair<StudioSyncRequestCompleted, StudioBuildActionResult> waitForSyncToFinish(StudioConnections connections, List<String> gradleArgs, List<String> jvmArgs) {
        System.out.println("* Sync has started, waiting for it to complete...");
        AtomicBoolean isSyncRequestCompleted = new AtomicBoolean();
        CompletableFuture<List<Duration>> gradleInvocations = CompletableFuture.supplyAsync(() -> collectGradleInvocations(connections, isSyncRequestCompleted, gradleArgs, jvmArgs), executor);
        StudioSyncRequestCompleted syncRequestCompleted = connections.getPluginConnection().receiveSyncRequestCompleted(SYNC_REQUEST_COMPLETED_TIMEOUT);
        isSyncRequestCompleted.set(true);
        List<Duration> gradleInvocationDurations = gradleInvocations.join();
        long totalGradleDuration = gradleInvocationDurations.stream()
            .mapToLong(Duration::toMillis)
            .sum();
        StudioBuildActionResult result = new StudioBuildActionResult(
            Duration.ofMillis(syncRequestCompleted.getDurationMillis()),
            Duration.ofMillis(totalGradleDuration),
            gradleInvocationDurations,
            Duration.ofMillis(syncRequestCompleted.getDurationMillis() - totalGradleDuration)
        );
        return Pair.of(syncRequestCompleted, result);
    }

    private List<Duration> collectGradleInvocations(StudioConnections connections, AtomicBoolean isSyncRequestCompleted, List<String> gradleArgs, List<String> jvmArgs) {
        List<Duration> durations = new ArrayList<>();
        int invocation = 1;
        ServerConnection agentConnection = connections.getAgentConnection();
        while (!isSyncRequestCompleted.get()) {
            Optional<GradleInvocationStarted> invocationStarted = agentConnection.maybeReceiveGradleInvocationStarted(GRADLE_INVOCATION_STARTED_TIMEOUT);
            if (invocationStarted.isPresent()) {
                System.out.printf("* Gradle invocation %s has started, waiting for it to complete...%n", invocation);
                agentConnection.send(new GradleInvocationParameters(gradleArgs, jvmArgs));
                GradleInvocationCompleted agentCompleted = agentConnection.receiveGradleInvocationCompleted(GRADLE_INVOCATION_COMPLETED_TIMEOUT);
                System.out.printf("* Gradle invocation %s has completed in: %sms%n", invocation++, agentCompleted.getDurationMillis());
                durations.add(Duration.ofMillis(agentCompleted.getDurationMillis()));
            }
        }
        return durations;
    }

    private boolean shouldCleanCache() {
        return isFirstRun && cleanCacheMode == CleanCacheMode.BEFORE_SCENARIO
            || cleanCacheMode == CleanCacheMode.BEFORE_BUILD;
    }

    @Override
    public void close() {
        try {
            executor.shutdown();
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

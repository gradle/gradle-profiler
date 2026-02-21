package org.gradle.profiler.ide;

import org.gradle.internal.Pair;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.client.protocol.ServerConnection;
import org.gradle.profiler.client.protocol.messages.*;
import org.gradle.profiler.instrument.GradleInstrumentation;
import org.gradle.profiler.result.BuildActionResult;
import org.gradle.profiler.ide.invoker.IdeBuildActionResult;
import org.gradle.profiler.ide.invoker.IdeGradleScenarioDefinition.IdeGradleBuildConfiguration;
import org.gradle.profiler.ide.process.IdeProcess.IdeConnections;
import org.gradle.profiler.ide.process.IdeProcessController;
import org.gradle.profiler.ide.tools.IdePluginInstaller;
import org.gradle.profiler.ide.tools.IdeSandboxCreator;
import org.gradle.profiler.ide.tools.IdeSandboxCreator.IdeSandbox;

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

import static org.gradle.profiler.client.protocol.messages.IdeRequest.IdeRequestType.*;
import static org.gradle.profiler.client.protocol.messages.IdeSyncRequestCompleted.IdeSyncRequestResult.FAILED;

public class IdeGradleClient implements GradleClient {

    public enum CleanCacheMode {
        BEFORE_SCENARIO,
        BEFORE_BUILD,
        NEVER
    }

    private static final Duration CACHE_CLEANUP_COMPLETED_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration GRADLE_INVOCATION_STARTED_TIMEOUT = Duration.ofMillis(100);
    private static final Duration GRADLE_INVOCATION_COMPLETED_TIMEOUT = Duration.ofMinutes(60);
    private static final Duration SYNC_REQUEST_COMPLETED_TIMEOUT = Duration.ofMinutes(90);

    private final IdeProcessController processController;
    private final IdePluginInstaller pluginInstaller;
    private final CleanCacheMode cleanCacheMode;
    private final ExecutorService executor;
    private final IdeSandbox sandbox;
    private boolean isFirstRun;

    public IdeGradleClient(IdeGradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, CleanCacheMode cleanCacheMode) {
        this.isFirstRun = true;
        this.cleanCacheMode = cleanCacheMode;
        Path installDir = invocationSettings.getIdeInstallDir().toPath();
        Optional<File> sandboxDir = invocationSettings.getIdeSandboxDir();
        this.sandbox = IdeSandboxCreator.createSandbox(sandboxDir.map(File::toPath).orElse(null));
        Path protocolJar = GradleInstrumentation.unpackPlugin("client-protocol").toPath();
        Path pluginJar = GradleInstrumentation.unpackPlugin("intellij-plugin").toPath();
        this.pluginInstaller = new IdePluginInstaller(sandbox.getPluginsDir());
        pluginInstaller.installPlugin(Arrays.asList(pluginJar, protocolJar));
        this.processController = new IdeProcessController(installDir, sandbox, invocationSettings, buildConfiguration);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public BuildActionResult sync(List<String> gradleArgs, List<String> jvmArgs) {
        if (shouldCleanCache()) {
            processController.runAndWaitToStop((connections) -> {
                System.out.println("* Cleaning IDE cache, this will require a restart...");
                connections.getPluginConnection().send(new IdeRequest(CLEANUP_CACHE));
                connections.getPluginConnection().receiveCacheCleanupCompleted(CACHE_CLEANUP_COMPLETED_TIMEOUT);
                connections.getPluginConnection().send(new IdeRequest(EXIT_IDE));
            });
        }

        isFirstRun = false;
        return processController.run((connections) -> {
            System.out.println("* Running sync in IDE...");
            connections.getPluginConnection().send(new IdeRequest(SYNC));
            System.out.println("* Sent sync request");
            Pair<IdeSyncRequestCompleted, IdeBuildActionResult> pair = waitForSyncToFinish(connections, gradleArgs, jvmArgs);
            IdeSyncRequestCompleted syncRequestResult = pair.getLeft();
            IdeBuildActionResult durationResult = pair.getRight();
            System.out.printf("* Full Gradle execution time: %dms%n", durationResult.getGradleTotalExecutionTime().toMillis());
            System.out.printf("* Full IDE execution time: %dms%n", durationResult.getIdeExecutionTime().toMillis());
            System.out.printf("* Full sync has completed in: %dms and it %s%n", durationResult.getExecutionTime().toMillis(), syncRequestResult.getResult());
            maybeThrownExceptionOnSyncFailure(syncRequestResult);
            return durationResult;
        });
    }

    private void maybeThrownExceptionOnSyncFailure(IdeSyncRequestCompleted syncRequestResult) {
        if (syncRequestResult.getResult() == FAILED) {
            throw new IllegalStateException(String.format("Gradle sync has failed with error message: '%s'. Full IDE logs can be found in: '%s'.",
                syncRequestResult.getErrorMessage(),
                new File(sandbox.getLogsDir().toFile(), "idea.log").getAbsolutePath())
            );
        }
    }

    private Pair<IdeSyncRequestCompleted, IdeBuildActionResult> waitForSyncToFinish(
        IdeConnections connections, List<String> gradleArgs, List<String> jvmArgs) {
        System.out.println("* Sync has started, waiting for it to complete...");
        AtomicBoolean isSyncRequestCompleted = new AtomicBoolean();
        CompletableFuture<List<Duration>> gradleInvocations = CompletableFuture.supplyAsync(() -> collectGradleInvocations(connections, isSyncRequestCompleted, gradleArgs, jvmArgs), executor);
        IdeSyncRequestCompleted syncRequestCompleted = connections.getPluginConnection().receiveSyncRequestCompleted(SYNC_REQUEST_COMPLETED_TIMEOUT);
        isSyncRequestCompleted.set(true);
        List<Duration> gradleInvocationDurations = gradleInvocations.join();
        long totalGradleDuration = gradleInvocationDurations.stream()
            .mapToLong(Duration::toMillis)
            .sum();
        IdeBuildActionResult result = new IdeBuildActionResult(
            Duration.ofMillis(syncRequestCompleted.getDurationMillis()),
            Duration.ofMillis(totalGradleDuration),
            gradleInvocationDurations,
            Duration.ofMillis(syncRequestCompleted.getDurationMillis() - totalGradleDuration)
        );
        return Pair.of(syncRequestCompleted, result);
    }

    private List<Duration> collectGradleInvocations(IdeConnections connections, AtomicBoolean isSyncRequestCompleted, List<String> gradleArgs, List<String> jvmArgs) {
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
                    System.out.println("* Stopping IDE...");
                    connections.getPluginConnection().send(new IdeRequest(EXIT_IDE));
                    System.out.println("* IDE stopped.");
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("* IDE did not finish successfully, you will have to close it manually.");
        } finally {
            pluginInstaller.uninstallPlugin();
        }
    }
}

package org.gradle.profiler.studio.process;

import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.client.protocol.messages.StudioAgentConnectionParameters;
import org.gradle.profiler.studio.invoker.StudioGradleScenarioDefinition.StudioGradleBuildConfiguration;
import org.gradle.profiler.studio.process.StudioProcess.StudioConnections;
import org.gradle.profiler.studio.tools.StudioSandboxCreator.StudioSandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Controls Studio process and connections.
 */
public class StudioProcessController {

    private final Path studioInstallDir;
    private final StudioSandbox sandbox;
    private final InvocationSettings invocationSettings;
    private final StudioGradleBuildConfiguration buildConfiguration;

    private StudioProcess process;

    public StudioProcessController(
        Path studioInstallDir,
        StudioSandbox sandbox,
        InvocationSettings invocationSettings,
        StudioGradleBuildConfiguration buildConfiguration
    ) {
        this.studioInstallDir = studioInstallDir;
        this.sandbox = sandbox;
        this.invocationSettings = invocationSettings;
        this.buildConfiguration = buildConfiguration;
    }

    /**
     * Runs actions on the connections to Android Studio. If Android Studio is not running, it will be started.
     */
    public <R> R run(Function<StudioConnections, R> action) {
        return run(maybeStartProcess(), action);
    }

    /**
     * Runs actions on the connections to Android Studio and stops the process.
     * If Android Studio is not running, it will be started and then stopped after action is done.
     */
    public void runAndWaitToStop(Consumer<StudioConnections> action) {
        try (StudioProcess process = maybeStartProcess()) {
            run(process, (connections) -> {
                action.accept(connections);
                return null;
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            process = null;
        }
    }

    private <R> R run(StudioProcess process, Function<StudioConnections, R> action) {
        return action.apply(process.getConnections());
    }

    public boolean isProcessRunning() {
        return process != null;
    }

    /**
     * Starts process if it was not started yet.
     */
    public StudioProcess maybeStartProcess() {
        if (!isProcessRunning()) {
            process = new StudioProcess(studioInstallDir, sandbox, invocationSettings, buildConfiguration.getStudioJvmArgs(), buildConfiguration.getIdeaProperties());
            process.getConnections().getAgentConnection().send(new StudioAgentConnectionParameters(buildConfiguration.getGradleHome()));
        }
        return process;
    }
}

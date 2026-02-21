package org.gradle.profiler.ide.process;

import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.client.protocol.messages.IdeAgentConnectionParameters;
import org.gradle.profiler.ide.invoker.IdeGradleScenarioDefinition.IdeGradleBuildConfiguration;
import org.gradle.profiler.ide.process.IdeProcess.IdeConnections;
import org.gradle.profiler.ide.tools.IdeSandboxCreator.IdeSandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Controls the IDE process and its connections.
 */
public class IdeProcessController {

    private final Path installDir;
    private final IdeSandbox sandbox;
    private final InvocationSettings invocationSettings;
    private final IdeGradleBuildConfiguration buildConfiguration;

    private IdeProcess process;

    public IdeProcessController(
        Path installDir,
        IdeSandbox sandbox,
        InvocationSettings invocationSettings,
        IdeGradleBuildConfiguration buildConfiguration
    ) {
        this.installDir = installDir;
        this.sandbox = sandbox;
        this.invocationSettings = invocationSettings;
        this.buildConfiguration = buildConfiguration;
    }

    /**
     * Runs actions on the connections to the IDE. If the IDE is not running, it will be started.
     */
    public <R> R run(Function<IdeConnections, R> action) {
        return run(maybeStartProcess(), action);
    }

    /**
     * Runs actions on the connections to the IDE and stops the process.
     * If the IDE is not running, it will be started and then stopped after action is done.
     */
    public void runAndWaitToStop(Consumer<IdeConnections> action) {
        try (IdeProcess process = maybeStartProcess()) {
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

    private <R> R run(IdeProcess process, Function<IdeConnections, R> action) {
        return action.apply(process.getConnections());
    }

    public boolean isProcessRunning() {
        return process != null;
    }

    /**
     * Starts process if it was not started yet.
     */
    public IdeProcess maybeStartProcess() {
        if (!isProcessRunning()) {
            process = new IdeProcess(installDir, sandbox, invocationSettings, buildConfiguration.getIdeJvmArgs(), buildConfiguration.getIdeaProperties());
            process.getConnections().getAgentConnection().send(new IdeAgentConnectionParameters(buildConfiguration.getGradleHome()));
        }
        return process;
    }
}

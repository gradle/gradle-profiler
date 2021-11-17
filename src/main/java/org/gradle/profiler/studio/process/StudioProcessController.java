package org.gradle.profiler.studio.process;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.client.protocol.Server;
import org.gradle.profiler.client.protocol.ServerConnection;
import org.gradle.profiler.client.protocol.messages.StudioAgentConnectionParameters;
import org.gradle.profiler.studio.LaunchConfiguration;
import org.gradle.profiler.studio.LauncherConfigurationParser;
import org.gradle.profiler.studio.tools.StudioSandboxCreator.StudioSandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Controls Studio process and connections.
 */
public class StudioProcessController {

    private static final Duration STUDIO_START_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration PLUGIN_CONNECT_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration AGENT_CONNECT_TIMEOUT = Duration.ofMinutes(1);

    private final Path studioInstallDir;
    private final StudioSandbox sandbox;
    private final InvocationSettings invocationSettings;
    private final GradleBuildConfiguration buildConfiguration;

    private Server studioAgentServer;
    private Server studioPluginServer;
    private StudioConnections connections;
    private CommandExec.RunHandle process;

    public StudioProcessController(Path studioInstallDir,
                                   StudioSandbox sandbox,
                                   InvocationSettings invocationSettings,
                                   GradleBuildConfiguration buildConfiguration) {
        this.studioInstallDir = studioInstallDir;
        this.sandbox = sandbox;
        this.invocationSettings = invocationSettings;
        this.buildConfiguration = buildConfiguration;
    }

    /**
     * Runs actions on the connections.
     */
    public <R> R run(Function<StudioConnections, R> action) {
        return action.apply(connections);
    }

    /**
     * Runs actions on the connections and stops the process
     */
    public <R> void runAndWaitToStop(Consumer<StudioConnections> action) {
        try (Server studioPluginServer = this.studioPluginServer;
             Server studioAgentServer = this.studioAgentServer) {
            run((connections) -> {
                action.accept(connections);
                return null;
            });
            process.waitForSuccess();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            process = null;
            studioPluginServer = null;
            studioAgentServer = null;
        }
    }

    /**
     * Starts process if it was not started yet.
     */
    public void maybeStartProcess() {
        if (process == null) {
            Server studioStartDetectorServer = new Server("start-detector");
            this.studioPluginServer = new Server("plugin");
            this.studioAgentServer = new Server("agent");
            LaunchConfiguration launchConfiguration = new LauncherConfigurationParser(studioInstallDir, sandbox)
                .installStudioPlugin(studioStartDetectorServer.getPort(), studioPluginServer.getPort())
                .installStudioAgent(studioAgentServer.getPort())
                .calculate();
            this.process = launchConfiguration.launchStudio(invocationSettings.getProjectDir());
            waitOnSuccessfulIdeStart(process, studioStartDetectorServer);
            ServerConnection studioPluginConnection = studioPluginServer.waitForIncoming(AGENT_CONNECT_TIMEOUT);
            ServerConnection studioAgentConnection = studioAgentServer.waitForIncoming(AGENT_CONNECT_TIMEOUT);
            this.connections = new StudioConnections(studioPluginConnection, studioAgentConnection);
            studioAgentConnection.send(new StudioAgentConnectionParameters(buildConfiguration.getGradleHome()));
        }
    }

    private void waitOnSuccessfulIdeStart(CommandExec.RunHandle runHandle, Server studioStartDetectorServer) {
        try (Server server = studioStartDetectorServer) {
            server.waitForIncoming(STUDIO_START_TIMEOUT);
        } catch (Exception e) {
            System.err.println("\n* ERROR\n" +
                "* Could not connect to Android Studio process started by the gradle-profiler.\n" +
                "* This might indicate that you are already running an Android Studio process in the same sandbox.\n" +
                "* Stop Android Studio manually in the used sandbox or use a different sandbox with --studio-sandbox-dir to isolate the process.\n");
            runHandle.kill();
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public static class StudioConnections {

        private final ServerConnection pluginConnection;
        private final ServerConnection agentConnection;

        public StudioConnections(ServerConnection pluginConnection, ServerConnection agentConnection) {
            this.pluginConnection = pluginConnection;
            this.agentConnection = agentConnection;
        }

        public ServerConnection getPluginConnection() {
            return pluginConnection;
        }

        public ServerConnection getAgentConnection() {
            return agentConnection;
        }
    }

}

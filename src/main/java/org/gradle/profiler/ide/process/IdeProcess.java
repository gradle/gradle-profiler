package org.gradle.profiler.ide.process;

import org.gradle.profiler.CommandExec.RunHandle;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.client.protocol.Server;
import org.gradle.profiler.client.protocol.ServerConnection;
import org.gradle.profiler.ide.launcher.IdeLauncher;
import org.gradle.profiler.ide.launcher.IdeLauncherProvider;
import org.gradle.profiler.ide.tools.IdeSandboxCreator.IdeSandbox;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class IdeProcess implements Closeable {

    private static final Duration IDE_START_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration PLUGIN_CONNECT_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration AGENT_CONNECT_TIMEOUT = Duration.ofMinutes(1);

    private final Server agentServer;
    private final Server pluginServer;
    private final IdeConnections connections;
    private final RunHandle process;

    public IdeProcess(Path installDir, IdeSandbox sandbox, InvocationSettings invocationSettings, List<String> ideJvmArgs, List<String> ideaProperties) {
        Server startDetectorServer = new Server("start-detector");
        this.pluginServer = new Server("plugin");
        this.agentServer = new Server("agent");
        IdeLauncher launcher = new IdeLauncherProvider(installDir, sandbox, ideJvmArgs, ideaProperties)
            .withPluginParameters(startDetectorServer.getPort(), pluginServer.getPort())
            .withAgentParameters(agentServer.getPort())
            .get();
        this.process = launcher.launch(invocationSettings.getProjectDir());
        waitOnSuccessfulIdeStart(process, startDetectorServer);
        connections = new IdeConnections(
            pluginServer.waitForIncoming(PLUGIN_CONNECT_TIMEOUT),
            agentServer.waitForIncoming(AGENT_CONNECT_TIMEOUT)
        );
    }

    private void waitOnSuccessfulIdeStart(RunHandle runHandle, Server startDetectorServer) {
        try (Server server = startDetectorServer) {
            server.waitForIncoming(IDE_START_TIMEOUT);
        } catch (Exception e) {
            System.err.println("\n* ERROR\n" +
                "* Could not connect to the IDE process started by the gradle-profiler.\n" +
                "* This might indicate that you are already running an IDE process in the same sandbox.\n" +
                "* Stop the IDE manually in the used sandbox or use a different sandbox with --ide-sandbox-dir to isolate the process.\n");
            kill(runHandle);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public IdeConnections getConnections() {
        return connections;
    }

    private void kill(RunHandle runHandle) {
        try {
            disconnect();
        } catch (Exception ignored) {
        } finally {
            runHandle.kill();
        }
    }

    private void disconnect() throws IOException {
        agentServer.close();
        pluginServer.close();
    }

    @Override
    public void close() throws IOException {
        disconnect();
        process.waitForSuccess();
    }

    public static class IdeConnections {

        private final ServerConnection pluginConnection;
        private final ServerConnection agentConnection;

        public IdeConnections(ServerConnection pluginConnection, ServerConnection agentConnection) {
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

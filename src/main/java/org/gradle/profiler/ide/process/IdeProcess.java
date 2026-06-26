package org.gradle.profiler.ide.process;

import org.gradle.profiler.CommandExec.RunHandle;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.client.protocol.Server;
import org.gradle.profiler.client.protocol.ServerConnection;
import org.gradle.profiler.ide.IdeType;
import org.gradle.profiler.ide.launcher.IdeLauncher;
import org.gradle.profiler.ide.launcher.IdeLauncherProvider;
import org.gradle.profiler.ide.tools.IdeSandboxCreator.IdeSandbox;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class IdeProcess implements Closeable {

    private static final Duration IDE_START_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration PLUGIN_CONNECT_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration AGENT_CONNECT_TIMEOUT = Duration.ofMinutes(1);

    private final Server ideAgentServer;
    private final Server idePluginServer;
    private final IdeConnections connections;
    private final RunHandle process;

    public IdeProcess(IdeType ideType, Path ideInstallDir, IdeSandbox sandbox, InvocationSettings invocationSettings, List<String> ideJvmArgs, List<String> ideaProperties) {
        Server ideStartDetectorServer = new Server("start-detector");
        this.idePluginServer = new Server("plugin");
        this.ideAgentServer = new Server("agent");
        IdeLauncher ideLauncher = new IdeLauncherProvider(ideType, ideInstallDir, sandbox, ideJvmArgs, ideaProperties)
            .withPluginParameters(ideStartDetectorServer.getPort(), idePluginServer.getPort())
            .withAgentParameters(ideAgentServer.getPort())
            .get();
        this.process = ideLauncher.launchIde(invocationSettings.getProjectDir());
        waitOnSuccessfulIdeStart(ideType, process, ideStartDetectorServer);
        connections = new IdeConnections(
            idePluginServer.waitForIncoming(PLUGIN_CONNECT_TIMEOUT),
            ideAgentServer.waitForIncoming(AGENT_CONNECT_TIMEOUT)
        );
    }

    private void waitOnSuccessfulIdeStart(IdeType ideType, RunHandle runHandle, Server ideStartDetectorServer) {
        try (Server server = ideStartDetectorServer) {
            server.waitForIncoming(IDE_START_TIMEOUT);
        } catch (Exception e) {
            String displayName = ideType.getDisplayName();
            System.err.println("\n* ERROR\n" +
                "* Could not connect to " + displayName + " process started by the gradle-profiler.\n" +
                "* This might indicate that you are already running a " + displayName + " process in the same sandbox.\n" +
                "* Stop " + displayName + " manually in the used sandbox or use a different sandbox to isolate the process.\n");
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
        ideAgentServer.close();
        idePluginServer.close();
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

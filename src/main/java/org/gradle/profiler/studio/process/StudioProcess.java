package org.gradle.profiler.studio.process;

import org.gradle.profiler.CommandExec.RunHandle;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.client.protocol.Server;
import org.gradle.profiler.client.protocol.ServerConnection;
import org.gradle.profiler.studio.launcher.StudioLauncher;
import org.gradle.profiler.studio.launcher.StudioLauncherProvider;
import org.gradle.profiler.studio.tools.StudioSandboxCreator.StudioSandbox;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class StudioProcess implements Closeable {

    private static final Duration STUDIO_START_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration PLUGIN_CONNECT_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration AGENT_CONNECT_TIMEOUT = Duration.ofMinutes(1);

    private final Server studioAgentServer;
    private final Server studioPluginServer;
    private final StudioConnections connections;
    private final RunHandle process;

    public StudioProcess(Path studioInstallDir, StudioSandbox sandbox, InvocationSettings invocationSettings, List<String> studioJvmArgs, List<String> ideaProperties) {
        Server studioStartDetectorServer = new Server("start-detector");
        this.studioPluginServer = new Server("plugin");
        this.studioAgentServer = new Server("agent");
        StudioLauncher studioLauncher = new StudioLauncherProvider(studioInstallDir, sandbox, studioJvmArgs, ideaProperties)
            .withStudioPluginParameters(studioStartDetectorServer.getPort(), studioPluginServer.getPort())
            .withStudioAgentParameters(studioAgentServer.getPort())
            .get();
        this.process = studioLauncher.launchStudio(invocationSettings.getProjectDir());
        waitOnSuccessfulIdeStart(process, studioStartDetectorServer);
        connections = new StudioConnections(
            studioPluginServer.waitForIncoming(PLUGIN_CONNECT_TIMEOUT),
            studioAgentServer.waitForIncoming(AGENT_CONNECT_TIMEOUT)
        );
    }

    private void waitOnSuccessfulIdeStart(RunHandle runHandle, Server studioStartDetectorServer) {
        try (Server server = studioStartDetectorServer) {
            server.waitForIncoming(STUDIO_START_TIMEOUT);
        } catch (Exception e) {
            System.err.println("\n* ERROR\n" +
                "* Could not connect to Android Studio process started by the gradle-profiler.\n" +
                "* This might indicate that you are already running an Android Studio process in the same sandbox.\n" +
                "* Stop Android Studio manually in the used sandbox or use a different sandbox with --studio-sandbox-dir to isolate the process.\n");
            kill(runHandle);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public StudioConnections getConnections() {
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
        studioAgentServer.close();
        studioPluginServer.close();
    }

    @Override
    public void close() throws IOException {
        disconnect();
        process.waitForSuccess();
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

package org.gradle.profiler.studio.process;

import com.google.common.base.Joiner;
import org.gradle.profiler.CommandExec;
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Logging;
import org.gradle.profiler.client.protocol.Server;
import org.gradle.profiler.client.protocol.ServerConnection;
import org.gradle.profiler.client.protocol.messages.StudioAgentConnectionParameters;
import org.gradle.profiler.studio.LaunchConfiguration;
import org.gradle.profiler.studio.LauncherConfigurationParser;
import org.gradle.profiler.studio.tools.StudioSandboxCreator.StudioSandbox;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Controls Studio process and connections.
 */
public class StudioProcessController {

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
            this.studioPluginServer = new Server("plugin");
            this.studioAgentServer = new Server("agent");
            LaunchConfiguration launchConfiguration = new LauncherConfigurationParser().calculate(studioInstallDir, sandbox, studioPluginServer.getPort());
            Logging.startOperation("Starting Android Studio at " + studioInstallDir);
            System.out.println();
            System.out.println("* Java command: " + launchConfiguration.getJavaCommand());
            System.out.println("* Classpath:");
            launchConfiguration.getClassPath().stream().map(entry -> "  " + entry).forEach(System.out::println);
            System.out.println("* System properties:");
            launchConfiguration.getSystemProperties().forEach((key, value) -> System.out.println("  " + key + " -> " + value));
            System.out.println("* Main class: " + launchConfiguration.getMainClass());
            this.process = startStudio(launchConfiguration, studioInstallDir, invocationSettings, studioAgentServer);
            ServerConnection studioPluginConnection = studioPluginServer.waitForIncoming(AGENT_CONNECT_TIMEOUT);
            ServerConnection studioAgentConnection = studioAgentServer.waitForIncoming(AGENT_CONNECT_TIMEOUT);
            this.connections = new StudioConnections(studioPluginConnection, studioAgentConnection);
            studioAgentConnection.send(new StudioAgentConnectionParameters(buildConfiguration.getGradleHome()));
        }
    }

    private CommandExec.RunHandle startStudio(LaunchConfiguration launchConfiguration, Path studioInstallDir, InvocationSettings invocationSettings, Server server) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(launchConfiguration.getJavaCommand().toString());
        commandLine.add("-cp");
        commandLine.add(Joiner.on(File.pathSeparator).join(launchConfiguration.getClassPath()));
        for (Map.Entry<String, String> systemProperty : launchConfiguration.getSystemProperties().entrySet()) {
            commandLine.add("-D" + systemProperty.getKey() + "=" + systemProperty.getValue());
        }
        commandLine.add("-javaagent:" + launchConfiguration.getAgentJar() + "=" + server.getPort() + "," + launchConfiguration.getSupportJar());
        commandLine.add("--add-exports");
        commandLine.add("java.base/jdk.internal.misc=ALL-UNNAMED");
        commandLine.add("-Xbootclasspath/a:" + Joiner.on(File.pathSeparator).join(launchConfiguration.getSharedJars()));
        commandLine.add(launchConfiguration.getMainClass());
        commandLine.add(invocationSettings.getProjectDir().getAbsolutePath());
        System.out.println("* Android Studio logs can be found at: " + Paths.get(launchConfiguration.getStudioLogsDir().toString(), "idea.log"));
        System.out.println("* Using command line: " + commandLine);
        return new CommandExec().inDir(studioInstallDir.toFile()).start(commandLine);
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

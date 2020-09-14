package org.gradle.profiler.studio;

import com.google.common.base.Joiner;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Logging;
import org.gradle.profiler.client.protocol.Server;
import org.gradle.profiler.client.protocol.ServerConnection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StudioGradleClient implements GradleClient {
    private final Server server;
    private final Process studioProcess;
    private final ServerConnection agentConnection;

    public StudioGradleClient(InvocationSettings invocationSettings) {
        Path studioInstallDir = invocationSettings.getStudioInstallDir().toPath();
        Logging.startOperation("Starting Android Studio at " + studioInstallDir);

        LaunchConfiguration launchConfiguration = new LauncherConfigurationParser().calculate(studioInstallDir);
        System.out.println();
        System.out.println("* Java command: " + launchConfiguration.getJavaCommand());
        System.out.println("* Classpath:");
        for (Path entry : launchConfiguration.getClassPath()) {
            System.out.println("  " + entry);
        }
        System.out.println("* System properties:");
        for (Map.Entry<String, String> entry : launchConfiguration.getSystemProperties().entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println("* Main class: " + launchConfiguration.getMainClass());

        server = new Server("agent");
        studioProcess = startStudio(launchConfiguration, studioInstallDir, server);
        agentConnection = server.waitForIncoming();
    }

    private Process startStudio(LaunchConfiguration launchConfiguration, Path studioInstallDir, Server server) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(launchConfiguration.getJavaCommand().toString());
        commandLine.add("-cp");
        commandLine.add(Joiner.on(File.pathSeparator).join(launchConfiguration.getClassPath()));
        for (Map.Entry<String, String> systemProperty : launchConfiguration.getSystemProperties().entrySet()) {
            commandLine.add("-D" + systemProperty.getKey() + "=" + systemProperty.getValue());
        }
        commandLine.add("-javaagent:" + launchConfiguration.getAgentJar() + "=" + server.getPort());
        commandLine.add("--add-exports");
        commandLine.add("java.base/jdk.internal.misc=ALL-UNNAMED");
        commandLine.add("-Xbootclasspath/a:" + launchConfiguration.getProtocolJar());
        commandLine.add(launchConfiguration.getMainClass());
        System.out.println("* Using command line: " + commandLine);

        try {
            return new ProcessBuilder(commandLine).inheritIO().directory(studioInstallDir.toFile()).start();
        } catch (IOException e) {
            throw new RuntimeException("Could not start Android Studio.", e);
        }
    }

    @Override
    public void close() throws IOException {
        System.out.println("* Waiting for Android Studio to stop...");
        try {
            studioProcess.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("* Android Studio stopped.");

        server.close();
    }

    public Duration sync() {
        System.out.println("* WARNING: Android Studio sync is not implemented yet.");
        return Duration.ZERO;
    }
}

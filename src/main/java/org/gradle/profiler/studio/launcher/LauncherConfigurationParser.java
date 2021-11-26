package org.gradle.profiler.studio.launcher;

import com.google.common.base.Joiner;
import org.gradle.profiler.instrument.GradleInstrumentation;
import org.gradle.profiler.studio.tools.StudioSandboxCreator.StudioSandbox;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LauncherConfigurationParser {

    private static final boolean SHOULD_RUN_HEADLESS = Boolean.getBoolean("studio.tests.headless");

    private final Path studioInstallDir;
    private final StudioSandbox studioSandbox;
    private boolean enableStudioPluginParameters;
    private int studioPluginPort;
    private boolean enableStudioAgentParameters;
    private int studioAgentPort;
    private int studioStartDetectorPort;

    public LauncherConfigurationParser(Path studioInstallDir, StudioSandbox studioSandbox) {
        this.studioInstallDir = studioInstallDir;
        this.studioSandbox = studioSandbox;
    }

    public LauncherConfigurationParser withStudioPluginParameters(int studioStartDetectorPort, int studioPluginPort) {
        this.enableStudioPluginParameters = true;
        this.studioStartDetectorPort = studioStartDetectorPort;
        this.studioPluginPort = studioPluginPort;
        return this;
    }

    public LauncherConfigurationParser withStudioAgentParameters(int studioAgentPort) {
        this.enableStudioAgentParameters = true;
        this.studioAgentPort = studioAgentPort;
        return this;
    }

    public LaunchConfiguration calculate() {
        StudioConfiguration studioConfiguration = StudioConfigurationProvider.getLaunchConfiguration(studioInstallDir);
        Map<String, String> systemProperties = buildSystemProperties(studioConfiguration.getSystemProperties());
        Path javaCommand = studioConfiguration.getJavaCommand();
        Path actualInstallDir = studioConfiguration.getActualInstallDir();

        Path agentJar = GradleInstrumentation.unpackPlugin("studio-agent").toPath();
        Path asmJar = GradleInstrumentation.unpackPlugin("asm").toPath();
        Path supportJar = GradleInstrumentation.unpackPlugin("instrumentation-support").toPath();
        Path protocolJar = GradleInstrumentation.unpackPlugin("client-protocol").toPath();
        Path studioPlugin = GradleInstrumentation.unpackPlugin("studio-plugin").toPath();
        Path studioPluginsDir = studioSandbox.getPluginsDir();
        Path studioLogsDir = studioSandbox.getLogsDir();
        List<Path> sharedJars = Arrays.asList(asmJar, protocolJar);
        List<Path> studioPluginJars = Arrays.asList(studioPlugin, protocolJar);

        String mainClass = studioConfiguration.getMainClass();
        List<Path> classpath = studioConfiguration.getClasspath();
        List<String> commandLine = new ArrayList<>();
        commandLine.add(javaCommand.toString());
        commandLine.add("-cp");
        commandLine.add(Joiner.on(File.pathSeparator).join(classpath));
        systemProperties.forEach((key, value) -> commandLine.add(String.format("-D%s=%s", key, value)));
        commandLine.add("-Xms256m");
        commandLine.add("-Xmx2048m");
        if (enableStudioAgentParameters) {
            commandLine.add(String.format("-javaagent:%s=%s,%s", agentJar, studioAgentPort, supportJar));
            commandLine.add("--add-exports");
            commandLine.add("java.base/jdk.internal.misc=ALL-UNNAMED");
            commandLine.add("-Xbootclasspath/a:" + Joiner.on(File.pathSeparator).join(sharedJars));
        }
        commandLine.add(mainClass);
        if (SHOULD_RUN_HEADLESS) {
            // Note: In headless mode ANDROID_HOME and ANDROID_SDK_ROOT have to be set otherwise
            // Studio will fail since "missing Android SDK" modal will try to open
            commandLine.add("headless-starter");
        }

        return new LaunchConfiguration(javaCommand, actualInstallDir, classpath, systemProperties, mainClass, agentJar, supportJar,
            sharedJars, studioPluginJars, studioPluginsDir, studioLogsDir, commandLine);
    }

    private Map<String, String> buildSystemProperties(Map<String, String> studioSystemProperties) {
        Map<String, String> systemProperties = new HashMap<>(studioSystemProperties);
        if (enableStudioPluginParameters) {
            systemProperties.put("gradle.profiler.startup.port", String.valueOf(studioStartDetectorPort));
            systemProperties.put("gradle.profiler.port", String.valueOf(studioPluginPort));
        }
        studioSandbox.getConfigDir().ifPresent(path -> systemProperties.put("idea.config.path", path.toString()));
        studioSandbox.getSystemDir().ifPresent(path -> systemProperties.put("idea.system.path", path.toString()));
        systemProperties.put("idea.plugins.path", studioSandbox.getPluginsDir().toString());
        systemProperties.put("idea.log.path", studioSandbox.getLogsDir().toString());
        if (SHOULD_RUN_HEADLESS) {
            systemProperties.put("java.awt.headless", "true");
        }
        return systemProperties;
    }
}

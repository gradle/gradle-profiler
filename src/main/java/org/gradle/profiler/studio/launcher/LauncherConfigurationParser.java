package org.gradle.profiler.studio.launcher;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
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
    private static final List<String> JAVA17_ADD_OPENS = ImmutableList.of(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/java.time=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
        "--add-opens=java.base/sun.security.util=ALL-UNNAMED",
        "--add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
        "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
        "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
        "--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED",
        "--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED",
        "--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED"
    );

    private final Path studioInstallDir;
    private final StudioSandbox studioSandbox;
    private final List<String> studioJvmArgs;
    private boolean enableStudioPluginParameters;
    private int studioPluginPort;
    private boolean enableStudioAgentParameters;
    private int studioAgentPort;
    private int studioStartDetectorPort;

    public LauncherConfigurationParser(Path studioInstallDir, StudioSandbox studioSandbox, List<String> studioJvmArgs) {
        this.studioInstallDir = studioInstallDir;
        this.studioSandbox = studioSandbox;
        this.studioJvmArgs = studioJvmArgs;
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
        // Support for IntelliJ with Java17 runtime
        commandLine.addAll(JAVA17_ADD_OPENS);
        if (enableStudioAgentParameters) {
            commandLine.add(String.format("-javaagent:%s=%s,%s", agentJar, studioAgentPort, supportJar));
            commandLine.add("--add-exports");
            commandLine.add("java.base/jdk.internal.misc=ALL-UNNAMED");
            commandLine.add("-Xbootclasspath/a:" + Joiner.on(File.pathSeparator).join(sharedJars));
        }
        commandLine.addAll(studioJvmArgs);
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
        // Newer IntelliJ versions require this property to avoid trust project popup
        systemProperties.put("idea.trust.all.projects", "true");
        // Used so wrapper init script is not run by Android Studio.
        // We anyway override installation in the org.gradle.profiler.studio.instrumented.Interceptor.
        systemProperties.put("idea.gradle.distributionType", "BUNDLED");
        if (SHOULD_RUN_HEADLESS) {
            systemProperties.put("java.awt.headless", "true");
        }
        return systemProperties;
    }
}

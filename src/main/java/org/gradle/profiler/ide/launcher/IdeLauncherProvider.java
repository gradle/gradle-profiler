package org.gradle.profiler.ide.launcher;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.instrument.GradleInstrumentation;
import org.gradle.profiler.ide.tools.IdeSandboxCreator.IdeSandbox;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IdeLauncherProvider {

    private static final boolean SHOULD_RUN_HEADLESS = Boolean.getBoolean("studio.tests.headless");
    private static final List<String> DEFAULT_MACOS_STARTER_PATHS = ImmutableList.of("Contents/MacOS/studio", "Contents/MacOS/idea");
    private static final List<String> DEFAULT_WINDOWS_STARTER_PATHS = ImmutableList.of("bin/studio.bat", "bin/idea.bat");
    private static final List<String> DEFAULT_LINUX_STARTER_PATHS = ImmutableList.of("bin/studio.sh", "bin/idea.sh");

    private final Path installDir;
    private final IdeSandbox sandbox;
    private final List<String> ideJvmArgs;
    private final List<String> ideaProperties;
    private boolean enablePluginParameters;
    private int pluginPort;
    private boolean enableAgentParameters;
    private int agentPort;
    private int startDetectorPort;

    public IdeLauncherProvider(Path installDir, IdeSandbox sandbox, List<String> ideJvmArgs, List<String> ideaProperties) {
        this.installDir = installDir;
        this.sandbox = sandbox;
        this.ideJvmArgs = ideJvmArgs;
        this.ideaProperties = ideaProperties;
    }

    public IdeLauncherProvider withPluginParameters(int startDetectorPort, int pluginPort) {
        this.enablePluginParameters = true;
        this.startDetectorPort = startDetectorPort;
        this.pluginPort = pluginPort;
        return this;
    }

    public IdeLauncherProvider withAgentParameters(int agentPort) {
        this.enableAgentParameters = true;
        this.agentPort = agentPort;
        return this;
    }

    public IdeLauncher get() {
        Path startCommand = getStartCommand(installDir);
        List<String> additionalJvmArgs = getAdditionalJvmArgs();

        // Note: In headless mode ANDROID_HOME and ANDROID_SDK_ROOT have to be set otherwise
        // Android Studio will fail since "missing Android SDK" modal will try to open
        String headlessCommand = SHOULD_RUN_HEADLESS ? "headless-starter" : "";

        return new IdeLauncher(startCommand, headlessCommand, installDir, additionalJvmArgs, sandbox, ideaProperties);
    }

    private List<String> getAdditionalJvmArgs() {
        List<String> jvmArgs = new ArrayList<>();
        if (enableAgentParameters) {
            String agentJar = GradleInstrumentation.unpackPlugin("studio-agent").getAbsolutePath();
            String supportJar = GradleInstrumentation.unpackPlugin("instrumentation-support").getAbsolutePath();
            String asmJar = GradleInstrumentation.unpackPlugin("asm").getAbsolutePath();
            String protocolJar = GradleInstrumentation.unpackPlugin("client-protocol").getAbsolutePath();
            List<String> sharedJars = Arrays.asList(asmJar, protocolJar);
            jvmArgs.add(String.format("-javaagent:%s=%s,%s", agentJar, agentPort, supportJar));
            jvmArgs.add("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");
            jvmArgs.add("-Xbootclasspath/a:" + Joiner.on(File.pathSeparator).join(sharedJars));
        }
        jvmArgs.addAll(ideJvmArgs);

        Map<String, String> systemProperties = new HashMap<>();
        if (enablePluginParameters) {
            systemProperties.put("gradle.profiler.startup.port", String.valueOf(startDetectorPort));
            systemProperties.put("gradle.profiler.port", String.valueOf(pluginPort));
        }
        sandbox.getConfigDir().ifPresent(path -> systemProperties.put("idea.config.path", path.toString()));
        sandbox.getSystemDir().ifPresent(path -> systemProperties.put("idea.system.path", path.toString()));
        systemProperties.put("idea.plugins.path", sandbox.getPluginsDir().toString());
        systemProperties.put("idea.log.path", sandbox.getLogsDir().toString());
        // Newer IntelliJ versions require this property to avoid trust project popup
        systemProperties.put("idea.trust.all.projects", "true");
        // Used so wrapper init script is not run by the IDE.
        // We anyway override installation in the org.gradle.profiler.ide.instrumented.Interceptor.
        systemProperties.put("idea.gradle.distributionType", "BUNDLED");
        if (SHOULD_RUN_HEADLESS) {
            systemProperties.put("java.awt.headless", "true");
        }
        systemProperties.put("external.system.auto.import.disabled", "true");
        systemProperties.forEach((k, v) -> jvmArgs.add(String.format("-D%s=%s", k, v)));
        return jvmArgs;
    }

    private static Path getStartCommand(Path installDir) {
        return getDefaultJavaPathsForOs().stream()
            .map(installDir::resolve)
            .filter(path -> path.toFile().exists())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Could not find Java executable in " + installDir));
    }

    private static List<String> getDefaultJavaPathsForOs() {
        if (OperatingSystem.isMacOS()) {
            return DEFAULT_MACOS_STARTER_PATHS;
        } else if (OperatingSystem.isWindows()) {
            return DEFAULT_WINDOWS_STARTER_PATHS;
        } else {
            return DEFAULT_LINUX_STARTER_PATHS;
        }
    }
}

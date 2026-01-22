package org.gradle.profiler.studio.launcher;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.instrument.GradleInstrumentation;
import org.gradle.profiler.studio.tools.StudioSandboxCreator.StudioSandbox;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudioLauncherProvider {

    private static final boolean SHOULD_RUN_HEADLESS = Boolean.getBoolean("studio.tests.headless");
    private static final List<String> DEFAULT_MACOS_STARTER_PATHS = ImmutableList.of("Contents/MacOS/studio", "Contents/MacOS/idea");
    private static final List<String> DEFAULT_WINDOWS_STARTER_PATHS = ImmutableList.of("bin/studio.bat", "bin/idea.bat");
    private static final List<String> DEFAULT_LINUX_STARTER_PATHS = ImmutableList.of("bin/studio.sh", "bin/idea.sh");

    private final Path studioInstallDir;
    private final StudioSandbox studioSandbox;
    private final List<String> studioJvmArgs;
    private final List<String> ideaProperties;
    private boolean enableStudioPluginParameters;
    private int studioPluginPort;
    private boolean enableStudioAgentParameters;
    private int studioAgentPort;
    private int studioStartDetectorPort;

    public StudioLauncherProvider(Path studioInstallDir, StudioSandbox studioSandbox, List<String> studioJvmArgs, List<String> ideaProperties) {
        this.studioInstallDir = studioInstallDir;
        this.studioSandbox = studioSandbox;
        this.studioJvmArgs = studioJvmArgs;
        this.ideaProperties = ideaProperties;
    }

    public StudioLauncherProvider withStudioPluginParameters(int studioStartDetectorPort, int studioPluginPort) {
        this.enableStudioPluginParameters = true;
        this.studioStartDetectorPort = studioStartDetectorPort;
        this.studioPluginPort = studioPluginPort;
        return this;
    }

    public StudioLauncherProvider withStudioAgentParameters(int studioAgentPort) {
        this.enableStudioAgentParameters = true;
        this.studioAgentPort = studioAgentPort;
        return this;
    }

    public StudioLauncher get() {
        Path startCommand = getStartCommand(studioInstallDir);
        List<String> additionalJvmArgs = getAdditionalJvmArgs();

        // Note: In headless mode ANDROID_HOME and ANDROID_SDK_ROOT have to be set otherwise
        // Studio will fail since "missing Android SDK" modal will try to open
        String headlessCommand = SHOULD_RUN_HEADLESS ? "headless-starter" : "";

        return new StudioLauncher(startCommand, headlessCommand, studioInstallDir, additionalJvmArgs, studioSandbox, ideaProperties);
    }

    private List<String> getAdditionalJvmArgs() {
        List<String> jvmArgs = new ArrayList<>();
        if (enableStudioAgentParameters) {
            String agentJar = GradleInstrumentation.unpackPlugin("studio-agent").getAbsolutePath();
            String supportJar = GradleInstrumentation.unpackPlugin("instrumentation-support").getAbsolutePath();
            String asmJar = GradleInstrumentation.unpackPlugin("asm").getAbsolutePath();
            String protocolJar = GradleInstrumentation.unpackPlugin("client-protocol").getAbsolutePath();
            List<String> sharedJars = Arrays.asList(asmJar, protocolJar);
            jvmArgs.add(String.format("-javaagent:%s=%s,%s", agentJar, studioAgentPort, supportJar));
            jvmArgs.add("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");
            jvmArgs.add("-Xbootclasspath/a:" + Joiner.on(File.pathSeparator).join(sharedJars));
        }
        jvmArgs.addAll(studioJvmArgs);

        Map<String, String> systemProperties = new HashMap<>();
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
        systemProperties.put("external.system.auto.import.disabled", "true");
        systemProperties.forEach((k, v) -> jvmArgs.add(String.format("-D%s=%s", k, v)));
        return jvmArgs;
    }

    private static Path getStartCommand(Path studioInstallDir) {
        return getDefaultJavaPathsForOs().stream()
            .map(studioInstallDir::resolve)
            .filter(path -> path.toFile().exists())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Could not find Java executable in " + studioInstallDir));
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

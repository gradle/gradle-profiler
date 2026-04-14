package org.gradle.profiler.ide.launcher;

import com.google.common.base.Joiner;
import org.gradle.profiler.instrument.GradleInstrumentation;
import org.gradle.profiler.ide.IdeType;
import org.gradle.profiler.ide.tools.IdeSandboxCreator.IdeSandbox;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IdeLauncherProvider {

    private static final boolean SHOULD_RUN_HEADLESS = Boolean.getBoolean("ide.tests.headless");

    private final IdeType ideType;
    private final Path ideInstallDir;
    private final IdeSandbox ideSandbox;
    private final List<String> ideJvmArgs;
    private final List<String> ideaProperties;
    private boolean enablePluginParameters;
    private int pluginPort;
    private boolean enableAgentParameters;
    private int agentPort;
    private int startDetectorPort;

    public IdeLauncherProvider(
        IdeType ideType,
        Path ideInstallDir,
        IdeSandbox ideSandbox,
        List<String> ideJvmArgs,
        List<String> ideaProperties
    ) {
        this.ideType = ideType;
        this.ideInstallDir = ideInstallDir;
        this.ideSandbox = ideSandbox;
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
        Path startCommand = getStartCommand(ideType, ideInstallDir);
        List<String> additionalJvmArgs = getAdditionalJvmArgs();

        String headlessCommand = SHOULD_RUN_HEADLESS ? "headless-starter" : "";

        return new IdeLauncher(ideType, startCommand, headlessCommand, ideInstallDir, additionalJvmArgs, ideSandbox, ideaProperties);
    }

    private List<String> getAdditionalJvmArgs() {
        List<String> jvmArgs = new ArrayList<>();
        if (enableAgentParameters) {
            String agentJar = GradleInstrumentation.unpackPlugin("ide-agent").getAbsolutePath();
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
        ideSandbox.getConfigDir().ifPresent(path -> systemProperties.put("idea.config.path", path.toString()));
        ideSandbox.getSystemDir().ifPresent(path -> systemProperties.put("idea.system.path", path.toString()));
        systemProperties.put("idea.plugins.path", ideSandbox.getPluginsDir().toString());
        systemProperties.put("idea.log.path", ideSandbox.getLogsDir().toString());
        // Newer IntelliJ versions require this property to avoid trust project popup
        systemProperties.put("idea.trust.all.projects", "true");
        // Used so wrapper init script is not run by IDE.
        // We anyway override installation in the org.gradle.profiler.ide.instrumented.Interceptor.
        systemProperties.put("idea.gradle.distributionType", "BUNDLED");
        if (SHOULD_RUN_HEADLESS) {
            systemProperties.put("java.awt.headless", "true");
        }
        systemProperties.put("external.system.auto.import.disabled", "true");
        systemProperties.forEach((k, v) -> jvmArgs.add(String.format("-D%s=%s", k, v)));
        return jvmArgs;
    }

    private static Path getStartCommand(IdeType ideType, Path ideInstallDir) {
        List<String> candidates = ideType.getStarterPathsForCurrentOs();
        return candidates.stream()
            .map(ideInstallDir::resolve)
            .filter(path -> path.toFile().exists())
            .findFirst()
            .orElseThrow(() -> new RuntimeException(
                "Expected " + ideType.getDisplayName() + " installation at " + ideInstallDir
                    + ", but no starter executable found at any of: " + candidates));
    }
}

package org.gradle.profiler.studio;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;
import com.google.common.base.Joiner;
import org.gradle.profiler.instrument.GradleInstrumentation;
import org.gradle.profiler.studio.tools.StudioSandboxCreator.StudioSandbox;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LauncherConfigurationParser {

    private final Path studioInstallDir;
    private final StudioSandbox studioSandbox;
    private boolean isInstallStudioPlugin;
    private int studioPluginPort;
    private boolean isInstallStudioAgent;
    private int studioAgentPort;

    public LauncherConfigurationParser(Path studioInstallDir, StudioSandbox studioSandbox) {
        this.studioInstallDir = studioInstallDir;
        this.studioSandbox = studioSandbox;
    }

    public LauncherConfigurationParser installStudioPlugin(int studioPluginPort) {
        this.isInstallStudioPlugin = true;
        this.studioPluginPort = studioPluginPort;
        return this;
    }

    public LauncherConfigurationParser installStudioAgent(int studioAgentPort) {
        this.isInstallStudioAgent = true;
        this.studioAgentPort = studioAgentPort;
        return this;
    }

    public LaunchConfiguration calculate() {
        Dict entries = parse(studioInstallDir.resolve("Contents/Info.plist"));
        Path actualInstallDir;
        if ("jetbrains-toolbox-launcher".equals(entries.string("CFBundleExecutable"))) {
            actualInstallDir = Paths.get(entries.string("JetBrainsToolboxApp"));
            entries = parse(actualInstallDir.resolve("Contents/Info.plist"));
        } else {
            actualInstallDir = studioInstallDir;
        }
        Dict jvmOptions = entries.dict("JVMOptions");
        List<Path> classPath = Arrays.stream(jvmOptions.string("ClassPath").split(":")).map(s -> FileSystems.getDefault().getPath(s.replace("$APP_PACKAGE", actualInstallDir.toString()))).collect(Collectors.toList());
        String mainClass = jvmOptions.string("MainClass");
        Map<String, String> systemProperties = buildSystemProperties(jvmOptions, actualInstallDir);
        Path javaCommand = actualInstallDir.resolve("Contents/jre/Contents/Home/bin/java");
        Path agentJar = GradleInstrumentation.unpackPlugin("studio-agent").toPath();
        Path asmJar = GradleInstrumentation.unpackPlugin("asm").toPath();
        Path supportJar = GradleInstrumentation.unpackPlugin("instrumentation-support").toPath();
        Path protocolJar = GradleInstrumentation.unpackPlugin("client-protocol").toPath();
        Path studioPlugin = GradleInstrumentation.unpackPlugin("studio-plugin").toPath();
        Path studioPluginsDir = studioSandbox.getPluginsDir();
        Path studioLogsDir = studioSandbox.getLogsDir();
        List<Path> sharedJars = Arrays.asList(asmJar, protocolJar);

        List<String> commandLine = buildCommandLine(
            javaCommand,
            classPath,
            mainClass,
            systemProperties,
            agentJar,
            supportJar,
            sharedJars
        );

        List<Path> studioPluginJars = isInstallStudioPlugin ? Arrays.asList(studioPlugin, protocolJar) : Collections.emptyList();
        return new LaunchConfiguration(javaCommand, studioInstallDir, classPath, systemProperties, mainClass, agentJar, supportJar,
            sharedJars, studioPluginJars, studioPluginsDir, studioLogsDir, commandLine);
    }

    private Map<String, String> buildSystemProperties(Dict jvmOptions, Path actualInstallDir) {
        Map<String, String> systemProperties = mapValues(jvmOptions.dict("Properties").toMap(), v -> v.replace("$APP_PACKAGE", actualInstallDir.toString()));
        if (isInstallStudioPlugin) {
            systemProperties.put("gradle.profiler.port", String.valueOf(studioPluginPort));
        }
        studioSandbox.getConfigDir().ifPresent(path -> systemProperties.put("idea.config.path", path.toString()));
        studioSandbox.getSystemDir().ifPresent(path -> systemProperties.put("idea.system.path", path.toString()));
        systemProperties.put("idea.plugins.path", studioSandbox.getPluginsDir().toString());
        systemProperties.put("idea.log.path", studioSandbox.getLogsDir().toString());
        return systemProperties;
    }

    private List<String> buildCommandLine(Path javaCommand,
                                          List<Path> classPath,
                                          String mainClass,
                                          Map<String, String> systemProperties,
                                          Path agentJar,
                                          Path supportJar,
                                          List<Path> sharedJars) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(javaCommand.toString());
        commandLine.add("-cp");
        commandLine.add(Joiner.on(File.pathSeparator).join(classPath));
        for (Map.Entry<String, String> systemProperty : systemProperties.entrySet()) {
            commandLine.add(String.format("-D%s=%s", systemProperty.getKey(), systemProperty.getValue()));
        }
        if (isInstallStudioAgent) {
            commandLine.add(String.format("-javaagent:%s=%s,%s", agentJar, studioAgentPort, supportJar));
            commandLine.add("--add-exports");
            commandLine.add("java.base/jdk.internal.misc=ALL-UNNAMED");
            commandLine.add("-Xbootclasspath/a:" + Joiner.on(File.pathSeparator).join(sharedJars));
        }
        commandLine.add(mainClass);
        return commandLine;
    }

    private static Dict parse(Path infoFile) {
        try {
            return new Dict((NSDictionary) PropertyListParser.parse(infoFile.toFile()));
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not parse '%s'.", infoFile), e);
        }
    }

    private static <T, S> Map<String, S> mapValues(Map<String, T> map, Function<T, S> mapper) {
        Map<String, S> result = new LinkedHashMap<>();
        for (Map.Entry<String, T> entry : map.entrySet()) {
            result.put(entry.getKey(), mapper.apply(entry.getValue()));
        }
        return result;
    }

    private static class Dict {
        private final NSDictionary contents;

        public Dict(NSDictionary contents) {
            this.contents = contents;
        }

        Dict dict(String key) {
            return new Dict((NSDictionary) getEntry(key));
        }

        String string(String key) {
            return ((NSString) getEntry(key)).getContent();
        }

        Map<String, String> toMap() {
            return mapValues(contents, v -> ((NSString) v).getContent());
        }

        private NSObject getEntry(String key) {
            NSObject value = contents.get(key);
            if (value == null) {
                throw new IllegalArgumentException(String.format("Dictionary does not contain entry '%s'.", key));
            }
            return value;
        }
    }

}

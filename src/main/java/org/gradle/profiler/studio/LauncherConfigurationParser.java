package org.gradle.profiler.studio;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.instrument.GradleInstrumentation;
import sun.tools.jar.resources.jar;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LauncherConfigurationParser {
    public LaunchConfiguration calculate(Path studioInstallDir, String pluginPort) {
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
        Map<String, String> systemProperties = mapValues(jvmOptions.dict("Properties").toMap(), v -> v.replace("$APP_PACKAGE", actualInstallDir.toString()));
        systemProperties.put("gradle.profiler.port", pluginPort);
        systemProperties.put("idea.trust.all.projects", "true");
        Path javaCommand = actualInstallDir.resolve("Contents/jre/Contents/Home/bin/java");
        Path agentJar = GradleInstrumentation.unpackPlugin("studio-agent").toPath();
        Path asmJar = GradleInstrumentation.unpackPlugin("asm").toPath();
        Path supportJar = GradleInstrumentation.unpackPlugin("instrumentation-support").toPath();
        Path protocolJar = GradleInstrumentation.unpackPlugin("client-protocol").toPath();
        Path studioPlugin = GradleInstrumentation.unpackPlugin("studio-plugin").toPath();
        Path studioPluginsDir = newPluginTempDir();
        systemProperties.put("idea.plugins.path", studioPluginsDir.toAbsolutePath().toString());
        return new LaunchConfiguration(javaCommand, classPath, systemProperties, mainClass, agentJar, supportJar, Arrays.asList(asmJar, protocolJar), Arrays.asList(studioPlugin, protocolJar), studioPluginsDir);
    }

    private static void copyJarsToDirectory(Path directory, Path... jars) {
        try {
            for (Path jar : jars) {
                String jarName = jar.getFileName().toString().endsWith(".jar")
                    ? jar.getFileName().toString()
                    : jar.getFileName() + ".jar";
                FileUtils.copyFile(jar.toFile(), new File(directory.toFile(), jarName));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteDirectory(Path directory, Path... jars) {
        try {
            for (Path jar : jars) {
                FileUtils.copyFileToDirectory(jar.toFile(), directory.toFile());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path newPluginTempDir() {
        try {
            return Files.createTempDirectory("plugins");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

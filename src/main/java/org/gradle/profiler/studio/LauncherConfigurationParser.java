package org.gradle.profiler.studio;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;
import org.gradle.profiler.instrument.GradleInstrumentation;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LauncherConfigurationParser {
    public LaunchConfiguration calculate(Path studioInstallDir) {
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
        Path javaCommand = actualInstallDir.resolve("Contents/jre/jdk/Contents/Home/bin/java");
        Path agentJar = GradleInstrumentation.unpackPlugin("studio-agent").toPath();
        Path asmJar = GradleInstrumentation.unpackPlugin("asm").toPath();
        Path supportJar = GradleInstrumentation.unpackPlugin("instrumentation-support").toPath();
        Path protocolJar = GradleInstrumentation.unpackPlugin("client-protocol").toPath();
        return new LaunchConfiguration(javaCommand, classPath, systemProperties, mainClass, agentJar, supportJar, Arrays.asList(asmJar, protocolJar));
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

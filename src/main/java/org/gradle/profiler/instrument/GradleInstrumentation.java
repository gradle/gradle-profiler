package org.gradle.profiler.instrument;

import org.gradle.internal.UncheckedException;
import org.gradle.profiler.GeneratedInitScript;
import org.gradle.profiler.GradleArgsCalculator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents some instrumentation that uses Gradle APIs and that is injected by gradle-profiler.
 */
public abstract class GradleInstrumentation implements GradleArgsCalculator {
    private GeneratedInitScript initScript;

    protected abstract void generateInitScriptBody(PrintWriter writer);

    @Override
    public void calculateGradleArgs(List<String> gradleArgs) {
        maybeGenerate();
        initScript.calculateGradleArgs(gradleArgs);
    }

    private void maybeGenerate() {
        File buildOpJar = unpackPlugin("build-operations");
        File chromeTraceJar = unpackPlugin("chrome-trace");
        File heapDumpJar = unpackPlugin("heap-dump");
        initScript = new GeneratedInitScript() {
            @Override
            public void writeContents(final PrintWriter writer) {
                writer.write("initscript {\n");
                writer.write("    dependencies {\n");
                writer.write("        classpath files('" + buildOpJar.toURI() + "', '" + chromeTraceJar.toURI() + "', '" + heapDumpJar.toURI() + "')\n");
                writer.write("    }\n");
                writer.write("}\n");
                writer.write("\n");
                generateInitScriptBody(writer);
            }
        };
    }

    public static File unpackPlugin(String jarName) {
        try {
            File pluginJar = File.createTempFile(jarName, ".jar").getCanonicalFile();
            try (InputStream inputStream = GradleInstrumentation.class.getResourceAsStream("/META-INF/jars/" + jarName + ".jar")) {
                Files.copy(inputStream, pluginJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            pluginJar.deleteOnExit();
            return pluginJar;
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static URL[] getClasspath(String classPathName) {
        List<String> classpathJars = readLines("/META-INF/classpath/" + classPathName + ".txt");
        return classpathJars.stream()
            .map(jar -> GradleInstrumentation.class.getResource("/META-INF/jars/" + jar))
            .toArray(URL[]::new);
    }

    private static List<String> readLines(String resourcePath) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(GradleInstrumentation.class.getResourceAsStream(resourcePath)))) {
            return reader.lines()
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

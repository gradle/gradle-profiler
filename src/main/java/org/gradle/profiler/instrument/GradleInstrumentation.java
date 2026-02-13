package org.gradle.profiler.instrument;

import org.gradle.internal.UncheckedException;
import org.gradle.profiler.GeneratedInitScript;
import org.gradle.profiler.GradleArgsCalculator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        // Dependency of build-operations
        File guavaJar = unpackPlugin("guava");
        File buildOpMeasurementsJar = unpackPlugin("build-operations-measuring");
        File chromeTraceJar = unpackPlugin("chrome-trace");
        File heapDumpJar = unpackPlugin("heap-dump");
        String filesExpr = Stream.of(buildOpJar, guavaJar, buildOpMeasurementsJar, chromeTraceJar, heapDumpJar)
            .map(f -> "'" + f.toURI().toASCIIString() + "'")
            .collect(Collectors.joining(", ", "files(", ")"));
        initScript = new GeneratedInitScript() {
            @Override
            public void writeContents(final PrintWriter writer) {
                writer.write("initscript {\n");
                writer.write("    dependencies {\n");
                writer.write("        classpath " + filesExpr + "\n");
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
}

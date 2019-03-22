package org.gradle.profiler;

import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

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
        initScript = new GeneratedInitScript() {
            @Override
            public void writeContents(final PrintWriter writer) {
                writer.write("initscript {\n");
                writer.write("    dependencies {\n");
                writer.write("        classpath files('" + buildOpJar.toURI() + "', '" + chromeTraceJar.toURI() + "')\n");
                writer.write("    }\n");
                writer.write("}\n");
                writer.write("\n");
                generateInitScriptBody(writer);
            }
        };
    }

    private File unpackPlugin(String jarName) {
        try {
            File pluginJar = File.createTempFile(jarName, "jar");
            try (InputStream inputStream = getClass().getResourceAsStream("/META-INF/jars/" + jarName + ".jar")) {
                Files.copy(inputStream, pluginJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            pluginJar.deleteOnExit();
            return pluginJar;
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}

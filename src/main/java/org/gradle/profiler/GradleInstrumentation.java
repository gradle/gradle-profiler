package org.gradle.profiler;

import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public abstract class GradleInstrumentation implements GradleArgsCalculator {
    private File pluginJar;
    private GeneratedInitScript initScript;

    protected abstract String getJarBaseName();

    protected abstract void generateInitScriptBody(PrintWriter writer);

    @Override
    public void calculateGradleArgs(List<String> gradleArgs) {
        maybeGenerate();
        initScript.calculateGradleArgs(gradleArgs);
    }

    private void maybeGenerate() {
        try {
            pluginJar = File.createTempFile(getJarBaseName(), "jar");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        unpackPlugin();
        pluginJar.deleteOnExit();
        initScript = new GeneratedInitScript() {
            @Override
            public void writeContents(final PrintWriter writer) {
                writer.write("initscript {\n");
                writer.write("    dependencies {\n");
                writer.write("        classpath files(\"" + pluginJar.toURI() + "\")\n");
                writer.write("    }\n");
                writer.write("}\n");
                writer.write("\n");
                generateInitScriptBody(writer);
            }
        };
    }

    private void unpackPlugin() {
        InputStream inputStream = getClass().getResourceAsStream("/META-INF/jars/" + getJarBaseName() + ".jar");
        try {
            Files.copy(inputStream, pluginJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}

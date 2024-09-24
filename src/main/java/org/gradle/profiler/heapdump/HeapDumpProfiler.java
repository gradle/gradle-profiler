package org.gradle.profiler.heapdump;

import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.instrument.GradleInstrumentation;

import java.io.File;
import java.io.PrintWriter;
import java.util.function.Consumer;

public class HeapDumpProfiler extends Profiler {
    @Override
    public boolean requiresGradle() {
        return true;
    }

    @Override
    public GradleArgsCalculator newGradleArgsCalculator(ScenarioSettings settings) {
        return new GradleInstrumentation() {
            @Override
            protected void generateInitScriptBody(PrintWriter writer) {
                File baseName = settings.profilerOutputLocationFor("");
                writer.println("new org.gradle.trace.heapdump.HeapDump(gradle, new File(new URI('" + baseName.toURI() + "')))");
            }
        };
    }

    @Override
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
        if (resultFile.getName().endsWith(".hprof") || resultFile.getName().endsWith(".jmap")) {
            consumer.accept(resultFile.getAbsolutePath());
        }
    }
}

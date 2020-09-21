package org.gradle.profiler.report;

import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;
import org.gradle.profiler.InvocationSettings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class HtmlGenerator extends AbstractGenerator {
    private static final String JSON_PLACEHOLDER = "@@BENCHMARK_RESULT_JSON@@";
    private static final String SCRIPT_PLACEHOLDER = "@@SCRIPT@@";

    public HtmlGenerator(File outputFile) {
        super(outputFile);
    }

    @Override
    protected void write(InvocationSettings settings, BenchmarkResult benchmarkResult, BufferedWriter writer) throws IOException {
        Resources.readLines(Resources.getResource(HtmlGenerator.class, "report-template.html"), StandardCharsets.UTF_8, new LineProcessor<Void>() {
            @Override
            public boolean processLine(String line) throws IOException {
                if (line.equals(SCRIPT_PLACEHOLDER)) {
                    Resources.asCharSource(Resources.getResource(HtmlGenerator.class, "report.js"), StandardCharsets.UTF_8).copyTo(writer);
                } else if (line.equals(JSON_PLACEHOLDER)) {
                    new JsonResultWriter(true).write(
                        settings.getBenchmarkTitle(),
                        Instant.now(),
                        benchmarkResult.getScenarios(),
                        writer
                    );
                } else {
                    writer.write(line);
                }
                writer.write("\n");
                return true;
            }

            @Override
            public Void getResult() {
                return null;
            }
        });
    }
}

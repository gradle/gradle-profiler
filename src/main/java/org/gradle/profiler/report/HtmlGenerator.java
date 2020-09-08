package org.gradle.profiler.report;

import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HtmlGenerator extends AbstractGenerator {
    private static final String JSON_PATTERN = "@@BENCHMARK_RESULT_JSON@@";

    public HtmlGenerator(File outputFile) {
        super(outputFile);
    }

    @Override
    protected void write(BenchmarkResult benchmarkResult, BufferedWriter writer) throws IOException {
        Resources.readLines(Resources.getResource(HtmlGenerator.class, "report-template.html"), StandardCharsets.UTF_8, new LineProcessor<Void>() {
            @Override
            public boolean processLine(String line) throws IOException {
                if (line.equals(JSON_PATTERN)) {
                    new JsonResultWriter(true).write(benchmarkResult.getScenarios(), writer);
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

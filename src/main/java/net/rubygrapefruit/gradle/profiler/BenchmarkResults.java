package net.rubygrapefruit.gradle.profiler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BenchmarkResults {
    private final Map<GradleVersion, List<BuildInvocationResult>> versions = new LinkedHashMap<>();

    public Consumer<BuildInvocationResult> version(GradleVersion version) {
        List<BuildInvocationResult> results = getResultsForVersion(version);
        return buildInvocationResult -> results.add(buildInvocationResult);
    }

    private List<BuildInvocationResult> getResultsForVersion(GradleVersion version) {
        List<BuildInvocationResult> results = versions.get(version);
        if (results == null) {
            results = new ArrayList<>();
            versions.put(version, results);
        }
        return results;
    }

    public void writeTo(File csv) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csv))) {
            writer.write("build");
            for (GradleVersion gradleVersion : versions.keySet()) {
                writer.write(",");
                writer.write(gradleVersion.getVersion());
            }
            writer.newLine();
            for (int row = 0; ; row++) {
                boolean startRow = true;
                for (List<BuildInvocationResult> results : versions.values()) {
                    if (row >= results.size()) {
                        return;
                    }
                    BuildInvocationResult buildResult = results.get(row);
                    if (startRow) {
                        writer.write(buildResult.getDisplayName());
                        startRow = false;
                    }
                    writer.write(",");
                    writer.write(String.valueOf(buildResult.getExecutionTime().toMillis()));
                }
                writer.newLine();
            }
        }
    }
}

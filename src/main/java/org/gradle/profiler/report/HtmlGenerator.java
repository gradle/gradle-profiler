package org.gradle.profiler.report;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.gradle.profiler.BuildInvocationResult;
import org.gradle.profiler.BuildScenarioResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HtmlGenerator extends AbstractGenerator {
    public HtmlGenerator(File outputFile) {
        super(outputFile);
    }

    @Override
    protected void write(List<? extends BuildScenarioResult> allScenarios, BufferedWriter writer) throws IOException {
        writer.write("<!DOCTYPE html>\n");
        writer.write("<html>\n");
        writer.write("<head>\n");
        writer.write("<title>Benchmark Results</title>\n");
        writer.write("<style>\n");
        writer.write("html, table { margin: 0; padding: 0 }\n");
        writer.write("body { padding: 20px; font-family: sans-serif; font-size: 11pt; color: #211F2D; }\n");
        writer.write("h1 { font-size: 16pt; }\n");
        writer.write("canvas { margin-top: 30px; margin-bottom: 30px; }\n");
        writer.write("table { border-collapse: collapse; margin-top: 30px; margin-bottom: 30px; }\n");
        writer.write("td { padding: 5px 10px 5px 10px; margin: 0; white-space: nowrap; }\n");
        writer.write("thead td { background-color: #6ea5ce; color: white; }\n");
        writer.write("tbody tr:nth-child(even) { background-color: #f0f0f0; }\n");
        writer.write("tfoot { border-top: 3px solid #8B899A; }\n");
        writer.write("</style>\n");
        writer.write("<script src='https://cdnjs.cloudflare.com/ajax/libs/Chart.js/2.7.2/Chart.bundle.min.js'></script>\n");
        writer.write("</head>\n");
        writer.write("<body>\n");
        writer.write("<h1>Benchmark results</h1>\n");
        writer.write("<canvas id='samples' width='900' height='400'></canvas>");
        writer.write("<table>\n");
        writer.write("<thead>\n");
        writer.write("<tr><td>Build</td>");
        for (BuildScenarioResult scenario : allScenarios) {
            writer.write("<td>");
            writer.write(scenario.getScenarioDefinition().getShortDisplayName());
            writer.write("</td>");
        }
        writer.write("</tr>\n");
        writer.write("<tr><td>Tasks</td>");
        for (BuildScenarioResult scenario : allScenarios) {
            writer.write("<td>");
            writer.write(scenario.getScenarioDefinition().getTasksDisplayName());
            writer.write("</td>");
        }
        writer.write("</tr>\n");
        writer.write("</thead>\n");

        writer.write("<tbody>\n");
        int maxRows = allScenarios.stream().mapToInt(v -> v.getResults().size()).max().orElse(0);
        for (int row = 0; row < maxRows; row++) {
            writer.write("<tr>");
            for (BuildScenarioResult result : allScenarios) {
                List<? extends BuildInvocationResult> results = result.getResults();
                if (row >= results.size()) {
                    continue;
                }
                BuildInvocationResult buildResult = results.get(row);
                writer.write("<td>");
                writer.write(buildResult.getDisplayName());
                writer.write("</td>");
                break;
            }
            for (BuildScenarioResult result : allScenarios) {
                List<? extends BuildInvocationResult> results = result.getResults();
                if (row >= results.size()) {
                    continue;
                }
                BuildInvocationResult buildResult = results.get(row);
                writer.write("<td>");
                writer.write(String.valueOf(buildResult.getExecutionTime().toMillis()));
                writer.write("</td>");
            }
            writer.write("</tr>\n");
        }
        writer.write("</tbody>\n");

        writer.write("<tfoot>\n");
        List<DescriptiveStatistics> statistics = allScenarios.stream().map(BuildScenarioResult::getStatistics).collect(Collectors.toList());
        writer.write("<tr><td>mean</td>");
        for (DescriptiveStatistics statistic : statistics) {
            writer.write("<td>");
            writer.write(String.valueOf(statistic.getMean()));
            writer.write("</td>");
        }
        writer.write("</tr>\n");
        writer.write("<tr><td>median</td>");
        for (DescriptiveStatistics statistic : statistics) {
            writer.write("<td>");
            writer.write(String.valueOf(statistic.getPercentile(50)));
            writer.write("</td>");
        }
        writer.write("</tr>\n");
        writer.write("<tr><td>stddev</td>");
        for (DescriptiveStatistics statistic : statistics) {
            writer.write("<td>");
            writer.write(String.valueOf(statistic.getStandardDeviation()));
            writer.write("</td>");
        }
        writer.write("</tr>\n");
        writer.write("</tfoot>\n");

        writer.write("</table>\n");

        writer.write("<script>\n");
        writer.write("var ctx = document.getElementById('samples').getContext('2d');\n");
        writer.write("var chart = new Chart(ctx, {\n");
        writer.write("    type: 'line',\n");
        writer.write("    data: {\n");
        writer.write("        labels: [");
        int maxIterations = allScenarios.stream().mapToInt(v -> v.getScenarioDefinition().getBuildCount()).max().orElse(0);
        for (int i = 0; i < maxIterations; i++) {
            writer.write(String.valueOf(i + 1));
            writer.write(",");
        }
        writer.write("],\n");
        writer.write("        datasets: [\n");
        PaletteGenerator generator = new PaletteGenerator();
        for (BuildScenarioResult scenario : allScenarios) {
            writer.write("{\n");
            writer.write("            label: '");
            writer.write(scenario.getScenarioDefinition().getShortDisplayName());
            writer.write("',\n");
            writer.write("            showLine: false,\n");
            writer.write("            fill: false,\n");
            writer.write("            borderWidth: 2,\n");
            writer.write("            borderColor: '");
            writer.write(generator.nextColor());
            writer.write("',\n");
            writer.write("            data: [");
            for (BuildInvocationResult buildResult : scenario.getMeasuredResults()) {
                writer.write(String.valueOf(buildResult.getExecutionTime().toMillis()));
                writer.write(",");
            }
            writer.write("]\n");
            writer.write("},\n");
        }
        writer.write("        ]\n");
        writer.write("    },\n");
        writer.write("    options: { responsive: false }\n");
        writer.write("});\n");
        writer.write("</script>\n");

        writer.write("</body>\n");
        writer.write("</html>\n");
    }

    static private class PaletteGenerator {
        private static final List<String> COLORS = Arrays.asList("#527AB3", "#56ac76", "#f44336", "#d49c3e", "#211f2d");
        private int next = 0;

        String nextColor() {
            if (next < COLORS.size()) {
                return COLORS.get(next++);
            }
            return "#8B899A";
        }
    }
}

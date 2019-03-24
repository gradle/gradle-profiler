package org.gradle.profiler.report;

import org.gradle.profiler.BuildInvocationResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class HtmlGenerator extends AbstractGenerator {
    private final NumberFormat numberFormat = new DecimalFormat("0.00");
    private final NumberFormat diffFormat = new DecimalFormat("+0.00;-0.00");

    public HtmlGenerator(File outputFile) {
        super(outputFile);
    }

    @Override
    protected void write(BenchmarkResult benchmarkResult, BufferedWriter writer) throws IOException {
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
        writer.write(".diff { font-size: 9pt; color: #d0d0d0; }\n");
        writer.write(".numeric { text-align: right; }\n");
        writer.write(".summary { vertical-align: top; }\n");
        writer.write("</style>\n");
        writer.write("<script src='https://cdnjs.cloudflare.com/ajax/libs/Chart.js/2.7.2/Chart.bundle.min.js'></script>\n");
        writer.write("</head>\n");
        writer.write("<body>\n");
        writer.write("<h1>Benchmark results</h1>\n");
        writer.write("<canvas id='samples' width='900' height='400'></canvas>");
        writer.write("<table>\n");
        writer.write("<thead>\n");
        writer.write("<tr><td>Scenario</td>");

        List<? extends BuildScenarioResult> allScenarios = benchmarkResult.getScenarios();
        for (BuildScenarioResult scenario : allScenarios) {
            writer.write("<td>");
            writer.write(scenario.getScenarioDefinition().getName());
            writer.write("</td>");
        }
        writer.write("</tr>\n");
        writer.write("<tr><td>Version</td>");
        for (BuildScenarioResult scenario : allScenarios) {
            writer.write("<td>");
            writer.write(scenario.getScenarioDefinition().getBuildToolDisplayName());
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
                writer.write("<td class='numeric'>");
                writer.write(String.valueOf(buildResult.getExecutionTime().toMillis()));
                writer.write("</td>");
            }
            writer.write("</tr>\n");
        }
        writer.write("</tbody>\n");

        writer.write("<tfoot>\n");
        statistic(writer, "mean", allScenarios, v -> v.getStatistics().getMean(), true);
        statistic(writer, "min", allScenarios, v -> v.getStatistics().getMin(), true);
        statistic(writer, "25th percentile", allScenarios, v -> v.getStatistics().getPercentile(25), true);
        statistic(writer, "median", allScenarios, v -> v.getStatistics().getPercentile(50), true);
        statistic(writer, "75th percentile", allScenarios, v -> v.getStatistics().getPercentile(75), true);
        statistic(writer, "max", allScenarios, v -> v.getStatistics().getMax(), true);
        statistic(writer, "stddev", allScenarios, v -> v.getStatistics().getStandardDeviation(), false);
        statistic(writer, "p-value (Mann Whitney U test)", allScenarios, v -> v.getBaseline().isPresent() ? v.getPValue() : 0d, false);
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
            writer.write(scenario.getScenarioDefinition().getName());
            writer.write(" ");
            writer.write(scenario.getScenarioDefinition().getBuildToolDisplayName());
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

    private void statistic(BufferedWriter writer, String name, List<? extends BuildScenarioResult> scenarios, Function<BuildScenarioResult, Double> value, boolean time) throws IOException {
        writer.write("<tr><td class='summary'>");
        writer.write(name);
        writer.write("</td>");
        for (BuildScenarioResult scenario : scenarios) {
            writer.write("<td class='numeric summary'>");
            double stat = value.apply(scenario);
            writer.write(numberFormat.format(stat));
            if (time && scenario.getBaseline().isPresent()) {
                writer.write("<br><span class='diff'>(");
                double baseLineStat = value.apply(scenario.getBaseline().get());
                double diff = stat - baseLineStat;
                writer.write(diffFormat.format(diff));
                writer.write(" ");
                writer.write(numberFormat.format((diff) / baseLineStat * 100));
                writer.write("%)</span>");
            }
            writer.write("</td>");
        }
        writer.write("</tr>");
        writer.newLine();
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

package org.gradle.profiler.report;

import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

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
        writer.write("<link rel=\"stylesheet\" href=\"https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css\" integrity=\"sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T\" crossorigin=\"anonymous\">");
        writer.write("<style>\n");
        writer.write(".diff { font-size: 9pt; color: #c0c0c0; }\n");
        writer.write(".numeric { text-align: right; }\n");
        writer.write(".summary { vertical-align: top; }\n");
        writer.write("</style>\n");
        writer.write("<script src='https://cdnjs.cloudflare.com/ajax/libs/Chart.js/2.7.2/Chart.bundle.min.js'></script>\n");
        writer.write("</head>\n");
        writer.write("<body>\n");
        writer.write("<div class='container mt-5 mb-5'>\n");
        writer.write("<h1>Benchmark results</h1>\n");

        writer.write("<div class='row mt-5'>\n");
        writer.write("<div class='col ml-auto mr-auto'>\n");
        writer.write("<canvas id='samples' width='900' height='400'></canvas>");
        writer.write("</div>\n");
        writer.write("</div>\n");

        writer.write("<div class='row mt-5'>\n");
        writer.write("<div class='col'>\n");
        writer.write("<table class='table table-sm table-hover'>\n");

        writer.write("<thead>\n");
        writer.write("<tr><th>Scenario</th>");
        List<? extends BuildScenarioResult> allScenarios = benchmarkResult.getScenarios();
        for (BuildScenarioResult scenario : allScenarios) {
            writer.write("<th colspan='" + scenario.getSamples().size() + "'>");
            writer.write(scenario.getScenarioDefinition().getTitle());
            writer.write("</th>");
        }
        writer.write("</tr>\n");
        writer.write("<tr><th>Version</th>");
        for (BuildScenarioResult scenario : allScenarios) {
            writer.write("<th colspan='" + scenario.getSamples().size() + "'>");
            writer.write(scenario.getScenarioDefinition().getBuildToolDisplayName());
            writer.write("</th>");
        }
        writer.write("</tr>\n");
        writer.write("<tr><th>Tasks</th>");
        for (BuildScenarioResult scenario : allScenarios) {
            writer.write("<th colspan='" + scenario.getSamples().size() + "'>");
            writer.write(scenario.getScenarioDefinition().getTasksDisplayName());
            writer.write("</th>");
        }
        writer.write("</tr>\n");
        writer.write("<tr><th>Sample</th>");
        for (BuildScenarioResult scenario : allScenarios) {
            for (Sample<?> sample : scenario.getSamples()) {
                writer.write("<th>");
                writer.write(sample.getName());
                writer.write("</th>");
            }
        }
        writer.write("</tr>\n");
        writer.write("</thead>\n");

        writer.write("<tbody>\n");

        int maxRows = allScenarios.stream().mapToInt(v -> v.getResults().size()).max().orElse(0);
        for (int row = 0; row < maxRows; row++) {
            writer.write("<tr>");
            for (BuildScenarioResult scenario : allScenarios) {
                List<? extends BuildInvocationResult> results = scenario.getResults();
                if (row >= results.size()) {
                    continue;
                }
                BuildInvocationResult buildResult = results.get(row);
                writer.write("<td>");
                writer.write(buildResult.getBuildContext().getDisplayName());
                writer.write("</td>");
                break;
            }
            for (BuildScenarioResult scenario : allScenarios) {
                List<? extends BuildInvocationResult> results = scenario.getResults();
                if (row >= results.size()) {
                    continue;
                }
                BuildInvocationResult buildResult = results.get(row);
                for (Sample<? super BuildInvocationResult> sample : scenario.getSamples()) {
                    writer.write("<td class='numeric'>");
                    writer.write(String.valueOf(sample.extractFrom(buildResult).toMillis()));
                    writer.write("</td>");
                }
            }
            writer.write("</tr>\n");
        }

        statistic(writer, "mean", allScenarios, BuildScenarioResult.Statistics::getMean, true);
        statistic(writer, "min", allScenarios, BuildScenarioResult.Statistics::getMin, true);
        statistic(writer, "25th percentile", allScenarios, s -> s.getPercentile(25), true);
        statistic(writer, "median", allScenarios, BuildScenarioResult.Statistics::getMedian, true);
        statistic(writer, "75th percentile", allScenarios, s -> s.getPercentile(75), true);
        statistic(writer, "max", allScenarios, BuildScenarioResult.Statistics::getMax, true);
        statistic(writer, "stddev", allScenarios, BuildScenarioResult.Statistics::getStandardDeviation, false);
        statistic(writer, "confidence", allScenarios, BuildScenarioResult.Statistics::getConfidencePercent, false);

        writer.write("</tbody>\n");

        writer.write("</table>\n");
        writer.write("</div>\n");
        writer.write("</div>\n");
        writer.write("</div>\n");

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
            writer.write(scenario.getScenarioDefinition().getTitle());
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

    private void statistic(BufferedWriter writer, String name, List<? extends BuildScenarioResult> scenarios, Function<BuildScenarioResult.Statistics, Double> value, boolean time) throws IOException {
        writer.write("<tr><td class='summary font-weight-bold'>");
        writer.write(name);
        writer.write("</td>");
        for (BuildScenarioResult scenario : scenarios) {
            for (int i = 0; i < scenario.getStatistics().size(); i++) {
                BuildScenarioResult.Statistics statistics = scenario.getStatistics().get(i);
                writer.write("<td class='numeric summary'>");
                double stat = value.apply(statistics);
                writer.write(numberFormat.format(stat));
                if (time && scenario.getBaseline().isPresent()) {
                    List<? extends BuildScenarioResult.Statistics> baselineStats = scenario.getBaseline().get().getStatistics();
                    if (!baselineStats.isEmpty()) {
                        writer.write("<br><span class='diff'>(");
                        double baseLineStat = value.apply(baselineStats.get(i));
                        double diff = stat - baseLineStat;
                        writer.write(diffFormat.format(diff));
                        writer.write(" ");
                        writer.write(numberFormat.format((diff) / baseLineStat * 100));
                        writer.write("%)</span>");
                    }
                }
                writer.write("</td>");
            }
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

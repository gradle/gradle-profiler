package org.gradle.profiler.report;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.gradle.profiler.BuildInvocationResult;
import org.gradle.profiler.BuildScenarioResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
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
        writer.write("body { padding: 20px; font-family: sans-serif; color: rgba(94,93,82,1); }\n");
        writer.write("h1 { font-size: 16pt; }\n");
        writer.write("table { border-collapse: collapse; }\n");
        writer.write("td { padding: 5px 10px 5px 10px; margin: 0; }\n");
        writer.write("thead td { background-color: rgba(29,150,178,1); color: white; }\n");
        writer.write("tbody tr:nth-child(even) { background-color: rgba(240,240,240,1); }\n");
        writer.write("tfoot { border-top: 3px solid rgba(60,120,180,1) }\n");
        writer.write("</style>\n");
        writer.write("</head>\n");
        writer.write("<body>\n");
        writer.write("<h1>Benchmark results</h1>\n");
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
        writer.write("</body>\n");
        writer.write("</html>\n");
    }
}

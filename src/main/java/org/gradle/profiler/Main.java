package org.gradle.profiler;

import org.gradle.profiler.instrument.PidInstrumentation;
import org.gradle.profiler.report.CsvGenerator;
import org.gradle.profiler.report.HtmlGenerator;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        boolean ok;
        try {
            new Main().run(args);
            ok = true;
        } catch (Exception e) {
            // Reported already
            ok = false;
        }
        System.exit(ok ? 0 : 1);
    }

    public void run(String[] args) throws Exception {
        try {
            Instant started = Instant.now();
            InvocationSettings settings = new CommandLineParser().parseSettings(args);

            System.out.println();
            System.out.println("* Writing results to " + settings.getOutputDir().getAbsolutePath());

            Logging.setupLogging(settings.getOutputDir());

            Logging.detailed().println();
            Logging.detailed().println("* Started at " + started);

            Logging.startOperation("Settings");
            settings.printTo(System.out);

            DaemonControl daemonControl = new DaemonControl(settings.getGradleUserHome());
            GradleBuildConfigurationReader gradleBuildConfigurationReader = new DefaultGradleBuildConfigurationReader(settings.getProjectDir(), settings.getGradleUserHome(), daemonControl);
            ScenarioLoader scenarioLoader = new ScenarioLoader(gradleBuildConfigurationReader);
            List<ScenarioDefinition> scenarios = scenarioLoader.loadScenarios(settings);
            int totalScenarios = scenarios.size();

            logScenarios(scenarios);

            File cvsFile = new File(settings.getOutputDir(), "benchmark.csv");
            File htmlFile = new File(settings.getOutputDir(), "benchmark.html");
            BenchmarkResultCollector benchmarkResults = new BenchmarkResultCollector(new CsvGenerator(cvsFile, settings.getCsvFormat()), new HtmlGenerator(htmlFile));
            PidInstrumentation pidInstrumentation = new PidInstrumentation();
            GradleScenarioInvoker gradleScenarioInvoker = new GradleScenarioInvoker(daemonControl, pidInstrumentation);
            BazelScenarioInvoker bazelScenarioInvoker = new BazelScenarioInvoker();
            BuckScenarioInvoker buckScenarioInvoker = new BuckScenarioInvoker();
            MavenScenarioInvoker mavenScenarioInvoker = new MavenScenarioInvoker();

            List<Throwable> failures = new ArrayList<>();
            int scenarioCount = 0;

            for (ScenarioDefinition scenario : scenarios) {
                scenarioCount++;
                Logging.startOperation("Running scenario " + scenario.getDisplayName() + " (scenario " + scenarioCount + "/" + totalScenarios + ")");
                ScenarioInvoker invoker;
                if (scenario instanceof BazelScenarioDefinition) {
                    invoker = bazelScenarioInvoker;
                } else if (scenario instanceof BuckScenarioDefinition) {
                    invoker = buckScenarioInvoker;
                } else if (scenario instanceof MavenScenarioDefinition) {
                    invoker = mavenScenarioInvoker;
                } else if (scenario instanceof GradleScenarioDefinition) {
                    invoker = gradleScenarioInvoker;
                } else {
                    throw new IllegalArgumentException("Don't know how to run scenario.");
                }
                try {
                    invoker.run(scenario, settings, benchmarkResults);
                } catch (Throwable t) {
                    t.printStackTrace();
                    failures.add(t);
                } finally {
                    // Write the current results and generate the reports, so that if this process crashes the results (which may have taken quite some time to collect) are not lost.
                    // This overwrites the existing reports, so may leave them in a corrupted state if this process crashes during the generation.
                    // This is just intended to be a simple best effort solution
                    if (settings.isBenchmark()) {
                        benchmarkResults.write();
                    }
                }
            }

            if (settings.isBenchmark()) {
                // Write the final results and generate the reports
                // This overwrites the existing reports, so may leave them in a corrupted state if this process crashes during the generation.
                benchmarkResults.write();
            }

            System.out.println();
            System.out.println("* Results written to " + settings.getOutputDir().getAbsolutePath());
            printResultFileSummaries(settings.getOutputDir(), settings.getProfiler());
            printReportSummary(settings, benchmarkResults);

            if (!failures.isEmpty()) {
                throw new ScenarioFailedException(failures.get(0));
            }
        } catch (CommandLineParser.SettingsNotAvailableException | ScenarioFailedException e) {
            // Reported already
            throw e;
        } catch (Exception e) {
            e.printStackTrace(System.out);
            throw e;
        } finally {
            System.out.println();
            System.out.flush();
        }
    }

    private void printReportSummary(InvocationSettings settings, BenchmarkResultCollector benchmarkResults) {
        if (settings.isBenchmark()) {
            benchmarkResults.summarizeResults(line -> System.out.println("  " + line));
        }
    }

    private void logScenarios(List<ScenarioDefinition> scenarios) {
        Logging.startOperation("Scenarios");
        for (ScenarioDefinition scenario : scenarios) {
            scenario.printTo(System.out);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static void printResultFileSummaries(File outputDir, Profiler profiler) {
        if (outputDir == null) {
            return;
        }
        for (File file : outputDir.listFiles()) {
            profiler.summarizeResultFile(file, line -> System.out.println("  " + line));
        }
        for (File file : outputDir.listFiles()) {
            if (file.isDirectory()) {
                printResultFileSummaries(file, profiler);
            }
        }
    }

    static class ScenarioFailedException extends RuntimeException {
        public ScenarioFailedException(Throwable cause) {
            super(cause);
        }
    }
}

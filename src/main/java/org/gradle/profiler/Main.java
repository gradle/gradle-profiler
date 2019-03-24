package org.gradle.profiler;

import org.gradle.profiler.instrument.PidInstrumentation;
import org.gradle.profiler.report.CsvGenerator;
import org.gradle.profiler.report.HtmlGenerator;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
            BenchmarkResultCollector benchmarkResults = new BenchmarkResultCollector(new CsvGenerator(cvsFile), new HtmlGenerator(htmlFile));
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
                Consumer<BuildInvocationResult> resultConsumer = benchmarkResults.version(scenario);
                try {
                    if (scenario instanceof BazelScenarioDefinition) {
                        bazelScenarioInvoker.run((BazelScenarioDefinition) scenario, settings, resultConsumer);
                    } else if (scenario instanceof BuckScenarioDefinition) {
                        buckScenarioInvoker.run((BuckScenarioDefinition) scenario, settings, resultConsumer);
                    } else if (scenario instanceof MavenScenarioDefinition) {
                        mavenScenarioInvoker.run((MavenScenarioDefinition) scenario, settings, resultConsumer);
                    } else {
                        gradleScenarioInvoker.run((GradleScenarioDefinition) scenario, settings, resultConsumer);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    failures.add(t);
                } finally {
                    // write the results up to this point
                    if (settings.isBenchmark()) {
                        benchmarkResults.write();
                    }
                }
            }

            if (settings.isBenchmark()) {
                benchmarkResults.write();
            }

            System.out.println();
            System.out.println("* Results written to " + settings.getOutputDir().getAbsolutePath());
            printResultFileSummaries(settings.getOutputDir(), settings.getProfiler());

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
            List<String> summary = profiler.summarizeResultFile(file);
            if (summary != null) {
                summary.forEach(line -> System.out.println("  " + line));
            }
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

package org.gradle.profiler;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class CommandLineParser {
    public static class SettingsNotAvailableException extends RuntimeException {
    }

    /**
     * Returns null on parse failure.
     */
    public InvocationSettings parseSettings(String[] args) throws IOException, SettingsNotAvailableException {
        OptionParser parser = new OptionParser();
        parser.nonOptions("The scenarios or task names to run");
        ArgumentAcceptingOptionSpec<File> projectOption = parser.accepts("project-dir", "The directory containing the build to run")
            .withRequiredArg().ofType(File.class).defaultsTo(new File("."));
        ArgumentAcceptingOptionSpec<String> versionOption = parser.accepts("gradle-version", "Gradle version or installation to use to run build")
            .withRequiredArg();
        ArgumentAcceptingOptionSpec<File> gradleUserHomeOption = parser.accepts("gradle-user-home", "The Gradle user home to use")
            .withRequiredArg()
            .ofType(File.class)
            .defaultsTo(new File("gradle-user-home"));
        ArgumentAcceptingOptionSpec<File> scenarioFileOption = parser.accepts("scenario-file", "Scenario definition file to use").withRequiredArg().ofType(File.class);
        ArgumentAcceptingOptionSpec<String> sysPropOption = parser.accepts("D", "Defines a system property").withRequiredArg();
        ArgumentAcceptingOptionSpec<File> outputDirOption = parser.accepts("output-dir", "Directory to write results to").withRequiredArg()
            .ofType(File.class).defaultsTo(findOutputDir());
        ArgumentAcceptingOptionSpec<Integer> warmupsOption = parser.accepts("warmups", "Number of warm-up build to run for each scenario").withRequiredArg().ofType(Integer.class);
        ArgumentAcceptingOptionSpec<Integer> iterationsOption = parser.accepts("iterations", "Number of builds to run for each scenario").withRequiredArg().ofType(Integer.class);
        ArgumentAcceptingOptionSpec<String> profilerOption = parser.accepts("profile",
            "Collect profiling information using profiler (" + Profiler.getAvailableProfilers().stream().collect(Collectors.joining(", ")) + ")")
            .withRequiredArg()
            .defaultsTo("jfr");
        Profiler.configureParser(parser);
        OptionSpecBuilder benchmarkOption = parser.accepts("benchmark", "Collect benchmark metrics");
        OptionSpecBuilder noDaemonOption = parser.accepts("no-daemon", "Do not use the Gradle daemon");
        OptionSpecBuilder coldDaemonOption = parser.accepts("cold-daemon", "Use a cold Gradle daemon");
        OptionSpecBuilder cliOption = parser.accepts("cli", "Uses the command-line client to connect to the daemon");
        OptionSpecBuilder dryRunOption = parser.accepts("dry-run", "Verify configuration");
        OptionSpecBuilder bazelOption = parser.accepts("bazel", "Benchmark scenarios using Bazel");
        OptionSpecBuilder buckOption = parser.accepts("buck", "Benchmark scenarios using buck");
        OptionSpecBuilder mavenOption = parser.accepts("maven", "Benchmark scenarios using Maven");

        OptionSet parsedOptions;
        try {
            parsedOptions = parser.parse(args);
        } catch (OptionException e) {
            return fail(parser, e.getMessage());
        }

        File projectDir = parsedOptions.valueOf(projectOption);
        boolean hasProfiler = parsedOptions.has(profilerOption);
        Profiler profiler = Profiler.NONE;
        if (hasProfiler) {
            List<String> profilersList = parsedOptions.valuesOf(profilerOption);
            profiler = Profiler.of(profilersList);
        }
        profiler = profiler.withConfig(parsedOptions);
        boolean benchmark = parsedOptions.has(benchmarkOption);
        if (!benchmark && !hasProfiler) {
            return fail(parser, "Neither --profile or --benchmark specified.");
        }

        File outputDir = parsedOptions.valueOf(outputDirOption);
        File gradleUserHome = parsedOptions.valueOf(gradleUserHomeOption);
        Integer warmups = parsedOptions.valueOf(warmupsOption);
        Integer iterations = parsedOptions.valueOf(iterationsOption);

        List<String> targetNames = parsedOptions.nonOptionArguments().stream().map(Object::toString).collect(Collectors.toList());
        List<String> versions = parsedOptions.valuesOf(versionOption);
        File scenarioFile = parsedOptions.valueOf(scenarioFileOption);

        // TODO - should validate the various combinations of invocation options
        Invoker invoker = Invoker.ToolingApi;
        if (parsedOptions.has(cliOption)) {
            invoker = Invoker.Cli;
        }
        if (parsedOptions.has(coldDaemonOption)) {
            if (invoker == Invoker.ToolingApi) {
                invoker = Invoker.ToolingApiColdDaemon;
            } else {
                invoker = Invoker.CliColdDaemon;
            }
        }
        if (parsedOptions.has(noDaemonOption)) {
            invoker = Invoker.CliNoDaemon;
        }
        if (parsedOptions.has(bazelOption)) {
            invoker = Invoker.Bazel;
        }
        if (parsedOptions.has(buckOption)) {
            invoker = Invoker.Buck;
        }
        if (parsedOptions.has(mavenOption)) {
            invoker = Invoker.Maven;
        }

        boolean dryRun = parsedOptions.has(dryRunOption);
        Map<String, String> sysProperties = new LinkedHashMap<>();
        for (String value : parsedOptions.valuesOf(sysPropOption)) {
            int sep = value.indexOf("=");
            if (sep < 0) {
                sysProperties.put(value, "true");
            } else {
                sysProperties.put(value.substring(0, sep), value.substring(sep + 1, value.length()));
            }
        }
        return new InvocationSettings(projectDir, profiler, benchmark, outputDir, invoker, dryRun, scenarioFile, versions, targetNames, sysProperties, gradleUserHome, warmups, iterations);
    }

    private File findOutputDir() {
        File outputDir = new File("profile-out");
        if (!outputDir.exists()) {
            return outputDir;
        }
        for (int i = 2; ; i++) {
            outputDir = new File("profile-out-" + i);
            if (!outputDir.exists()) {
                return outputDir;
            }
        }
    }

    private InvocationSettings fail(OptionParser parser, String message) throws IOException {
        System.out.println(message);
        System.out.println();
        parser.printHelpOn(System.out);
        throw new SettingsNotAvailableException();
    }
}

package org.gradle.profiler;

import joptsimple.*;

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
        ArgumentAcceptingOptionSpec<String> projectOption = parser.accepts("project-dir", "The directory containing the build to run")
                .withRequiredArg();
        ArgumentAcceptingOptionSpec<String> versionOption = parser.accepts("gradle-version", "Gradle version or installation to use to run build")
                .withRequiredArg();
        ArgumentAcceptingOptionSpec<String> gradleUserHomeOption = parser.accepts("gradle-user-home", "The Gradle user home to use")
                .withRequiredArg();
        ArgumentAcceptingOptionSpec<String> scenarioFileOption = parser.accepts("scenario-file", "Scenario definition file to use").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> sysPropOption = parser.accepts("D", "Defines a system property").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> outputDirOption = parser.accepts("output-dir", "Directory to write results to").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> warmupsOption = parser.accepts("warmups", "Number of warm-up build to run for each scenario").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> iterationsOption = parser.accepts("iterations", "Number of builds to run for each scenario").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> profilerOption = parser.accepts("profile",
                "Collect profiling information using profiler (" + Profiler.getAvailableProfilers().stream().collect(Collectors.joining(", ")) + ")")
                .withRequiredArg()
                .defaultsTo("jfr");
        Profiler.configureParser(parser);
        OptionSpecBuilder benchmarkOption = parser.accepts("benchmark", "Collect benchmark metrics");
        OptionSpecBuilder noDaemonOption = parser.accepts("no-daemon", "Do not use the Gradle daemon");
        OptionSpecBuilder dryRunOption = parser.accepts("dry-run", "Verify configuration");
        OptionSpecBuilder buckOption = parser.accepts("buck", "Benchmark scenarios using buck");
        OptionSet parsedOptions;
        try {
            parsedOptions = parser.parse(args);
        } catch (OptionException e) {
            return fail(parser, e.getMessage());
        }

        if (!parsedOptions.has(projectOption)) {
            return fail(parser, "No project directory specified.");
        }

        File projectDir = (parsedOptions.has(projectOption) ? new File(parsedOptions.valueOf(projectOption)) : new File(".")).getCanonicalFile();
        boolean hasProfiler = parsedOptions.has(profilerOption);
        Profiler profiler = Profiler.NONE;
        if (hasProfiler) {
            List<String> profilersList = parsedOptions.valuesOf(profilerOption);
            profiler = Profiler.of(profilersList);
        }
        Object profilerOptions = profiler.newConfigObject(parsedOptions);
        boolean benchmark = parsedOptions.has(benchmarkOption);
        if (!benchmark && !hasProfiler) {
            return fail(parser, "Neither --profile or --benchmark specified.");
        }

        File outputDir;
        if (parsedOptions.has(outputDirOption)) {
            outputDir = new File(parsedOptions.valueOf(outputDirOption));
        } else {
            outputDir = findOutputDir();
        }
        File gradleUserHome;
        if (parsedOptions.has(gradleUserHomeOption)) {
            gradleUserHome = new File(parsedOptions.valueOf(gradleUserHomeOption));
        } else {
            gradleUserHome = new File("gradle-user-home");
        }
        Integer warmups = null;
        if (parsedOptions.has(warmupsOption)) {
            warmups = Integer.valueOf(parsedOptions.valueOf(warmupsOption));
        }
        Integer iterations = null;
        if (parsedOptions.has(iterationsOption)) {
            iterations = Integer.valueOf(parsedOptions.valueOf(iterationsOption));
        }

        List<String> targetNames = parsedOptions.nonOptionArguments().stream().map(o -> o.toString()).collect(Collectors.toList());
        List<String> versions = parsedOptions.valuesOf(versionOption).stream().map(v -> v.toString()).collect(Collectors.toList());
        File scenarioFile = parsedOptions.has(scenarioFileOption) ? new File(parsedOptions.valueOf(scenarioFileOption)) : null;
        Invoker invoker = Invoker.ToolingApi;
        if (parsedOptions.has(noDaemonOption)) {
            invoker = Invoker.NoDaemon;
        }
        if (parsedOptions.has(buckOption)) {
            invoker = Invoker.Buck;
        }
        boolean dryRun = parsedOptions.has(dryRunOption);
        Map<String, String> sysProperties = new LinkedHashMap<>();
        for (String value : parsedOptions.valuesOf(sysPropOption)) {
            String[] parts = value.split("\\s*=\\s*");
            if (parts.length == 1) {
                sysProperties.put(parts[0], "true");
            } else {
                sysProperties.put(parts[0], parts[1]);
            }
        }
        return new InvocationSettings(projectDir, profiler, profilerOptions, benchmark, outputDir, invoker, dryRun, scenarioFile, versions, targetNames, sysProperties, gradleUserHome, warmups, iterations);
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

package org.gradle.profiler;

import joptsimple.*;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

class CommandLineParser {
    public static class SettingsNotAvailableException extends RuntimeException {
    }

    /**
     * Returns null on parse failure.
     */
    public InvocationSettings parseSettings(String[] args) throws IOException, SettingsNotAvailableException {
        OptionParser parser = new OptionParser();
        ArgumentAcceptingOptionSpec<String> projectOption = parser.accepts("project-dir", "The directory containing the build to run")
                .withRequiredArg();
        ArgumentAcceptingOptionSpec<String> versionOption = parser.accepts("gradle-version", "Gradle version or installation to use to run build")
                .withRequiredArg();
        ArgumentAcceptingOptionSpec<String> scenarioFileOption = parser.accepts("scenario-file", "Scenario definition file to use").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> sysPropOption = parser.accepts("D", "Defines a system property").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> outputDirOption = parser.accepts("output-dir", "Directory to write results to").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> profilerOption = parser.accepts("profile",
                "Collect profiling information using profiler (" + Profiler.getAvailableProfilers().stream().reduce("", (s, s2) -> s + ", " + s2) + ")")
                .withRequiredArg()
                .defaultsTo("jfr");
        Profiler.configureParser(parser);
        OptionSpecBuilder benchmarkOption = parser.accepts("benchmark", "Collect benchmark metrics");
        OptionSpecBuilder noDaemonOption = parser.accepts("no-daemon", "Do not use the Gradle daemon");
        OptionSpecBuilder dryRunOption = parser.accepts("dry-run", "Verify configuration");
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

        List<String> taskNames = parsedOptions.nonOptionArguments().stream().map(o -> o.toString()).collect(Collectors.toList());
        List<String> versions = parsedOptions.valuesOf(versionOption).stream().map(v -> v.toString()).collect(Collectors.toList());
        File scenarioFile = parsedOptions.has(scenarioFileOption) ? new File(parsedOptions.valueOf(scenarioFileOption)) : null;
        Invoker invoker = parsedOptions.has(noDaemonOption) ? Invoker.NoDaemon : Invoker.ToolingApi;
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
        return new InvocationSettings(projectDir, profiler, profilerOptions, benchmark, outputDir, invoker, dryRun, scenarioFile, versions, taskNames, sysProperties);
    }

    private File findOutputDir() {
        File outputDir = new File("profile-out");
        if (!outputDir.exists()) {
            return outputDir;
        }
        for (int i = 1; ; i++) {
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

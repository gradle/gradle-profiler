package net.rubygrapefruit.gradle.profiler;

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
        ArgumentAcceptingOptionSpec<String> projectOption = parser.accepts("project-dir", "The directory containing the build to run")
                .withRequiredArg();
        ArgumentAcceptingOptionSpec<String> versionOption = parser.accepts("gradle-version", "Gradle version or installation to use to run build")
                .withRequiredArg();
        ArgumentAcceptingOptionSpec<String> configFileOption = parser.accepts("config-file", "Configuration file to use").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> sysPropOption = parser.accepts("D", "Defines a system property").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> outputDirOption = parser.accepts("output-dir", "Directory to write results to").withRequiredArg();
        OptionSpecBuilder jfrOption = parser.accepts("profile", "Collect profiling information using JFR");
        OptionSpecBuilder benchmarkOption = parser.accepts("benchmark", "Collect benchmark metrics");
        OptionSpecBuilder noDaemon = parser.accepts("no-daemon", "Do not use the Gradle daemon");
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
        boolean profile = parsedOptions.has(jfrOption);
        boolean benchmark = parsedOptions.has(benchmarkOption);
        if (!benchmark && !profile) {
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
        File configFile = parsedOptions.has(configFileOption) ? new File(parsedOptions.valueOf(configFileOption)) : null;
        Invoker invoker = parsedOptions.has(noDaemon) ? Invoker.NoDaemon : Invoker.ToolingApi;
        Map<String, String> sysProperties = new LinkedHashMap<>();
        for (String value : parsedOptions.valuesOf(sysPropOption)) {
            String[] parts = value.split("\\s*=\\s*");
            if (parts.length == 1) {
                sysProperties.put(parts[0], "true");
            } else {
                sysProperties.put(parts[0], parts[1]);
            }
        }
        return new InvocationSettings(projectDir, profile, benchmark, outputDir, invoker, configFile, versions, taskNames, sysProperties);
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

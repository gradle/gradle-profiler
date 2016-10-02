package net.rubygrapefruit.gradle.profiler;

import joptsimple.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

class CommandLineParser {
    /**
     * Returns null on parse failure.
     */
    public InvocationSettings parseSettings(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        ArgumentAcceptingOptionSpec<String> projectOption = parser.accepts("project-dir", "the directory to run the build from").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> versionOption = parser.accepts("gradle-version", "Gradle version or installation to use to run build").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> configFileOption = parser.accepts("config-file", "Configuration file to use").withRequiredArg();
        OptionSpecBuilder jfrOption = parser.accepts("profile", "collect profiling information using JFR (default)");
        OptionSpecBuilder benchmarkOption = parser.accepts("benchmark", "collect benchmark metrics");
        OptionSpecBuilder noDaemon = parser.accepts("no-daemon", "do not use the Gradle daemon");
        OptionSet parsedOptions;
        try {
            parsedOptions = parser.parse(args);
        } catch (OptionException e) {
            System.out.println(e.getMessage());
            System.out.println();
            parser.printHelpOn(System.out);
            return null;
        }
        if (!parsedOptions.has(projectOption)) {
            System.out.println("No project directory specified.");
            System.out.println();
            parser.printHelpOn(System.out);
            return null;
        }

        File projectDir = (parsedOptions.has(projectOption) ? new File(parsedOptions.valueOf(projectOption)) : new File(".")).getCanonicalFile();
        boolean profile = parsedOptions.has(jfrOption);
        boolean benchmark = parsedOptions.has(benchmarkOption);
        if (!benchmark) {
            profile = true;
        }
        List<String> taskNames = parsedOptions.nonOptionArguments().stream().map(o -> o.toString()).collect(Collectors.toList());
        List<String> versions = parsedOptions.valuesOf(versionOption).stream().map(v -> v.toString()).collect(Collectors.toList());
        File configFile = parsedOptions.has(configFileOption) ? new File(parsedOptions.valueOf(configFileOption)) : null;
        Invoker invoker = parsedOptions.has(noDaemon) ? Invoker.NoDaemon : Invoker.ToolingApi;
        return new InvocationSettings(projectDir, profile, benchmark, invoker, configFile, versions, taskNames);
    }
}

package org.gradle.profiler;

import joptsimple.AbstractOptionSpec;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.gradle.profiler.report.CsvGenerator;
import org.gradle.profiler.report.CsvGenerator.Format;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CommandLineParser {

    public static class SettingsNotAvailableException extends RuntimeException {
    }

    /**
     * Returns null on parse failure.
     */
    @Nullable
    public InvocationSettings parseSettings(String[] args) throws IOException, SettingsNotAvailableException {
        OptionParser parser = new OptionParser();
        AbstractOptionSpec<Void> helpOption = parser.acceptsAll(Arrays.asList("h", "help"), "Show this usage information")
            .forHelp();
        AbstractOptionSpec<Void> versionOption = parser.acceptsAll(Arrays.asList("v", "version"), "Display version information");
        parser.nonOptions("The scenarios or task names to run");
        ArgumentAcceptingOptionSpec<File> projectOption = parser.accepts("project-dir", "The directory containing the build to run")
            .withRequiredArg().ofType(File.class).defaultsTo(new File(".").getCanonicalFile());
        ArgumentAcceptingOptionSpec<String> gradleVersionOption = parser.accepts("gradle-version", "Gradle version or installation to use to run build")
            .withRequiredArg();
        ArgumentAcceptingOptionSpec<File> gradleUserHomeOption = parser.accepts("gradle-user-home", "The Gradle user home to use")
            .withRequiredArg()
            .ofType(File.class)
            .defaultsTo(new File("gradle-user-home"));
        ArgumentAcceptingOptionSpec<File> studioHomeOption = parser.accepts("studio-install-dir", "The Studio installation to use").withRequiredArg().ofType(File.class);
        ArgumentAcceptingOptionSpec<File> studioSandboxOption = parser.accepts("studio-sandbox-dir", "The Studio sandbox dir to use").withRequiredArg().ofType(File.class);
        OptionSpecBuilder disableStudioSandbox = parser.accepts("no-studio-sandbox", "Marks that Android Studio should not use sandbox");
        ArgumentAcceptingOptionSpec<File> scenarioFileOption = parser.accepts("scenario-file", "Scenario definition file to use").withRequiredArg().ofType(File.class);
        ArgumentAcceptingOptionSpec<String> sysPropOption = parser.accepts("D", "Defines a system property").withRequiredArg();
        ArgumentAcceptingOptionSpec<File> outputDirOption = parser.accepts("output-dir", "Directory to write results to").withRequiredArg()
            .ofType(File.class).defaultsTo(findOutputDir("profile-out"));
        ArgumentAcceptingOptionSpec<Integer> warmupsOption = parser.accepts("warmups", "Number of warm-up build to run for each scenario").withRequiredArg().ofType(Integer.class);
        ArgumentAcceptingOptionSpec<Integer> iterationsOption = parser.accepts("iterations", "Number of builds to run for each scenario").withRequiredArg().ofType(Integer.class);
        ArgumentAcceptingOptionSpec<String> profilerOption = parser.accepts("profile",
            "Collect profiling information using profiler (" + String.join(", ", ProfilerFactory.getAvailableProfilers()) + ")")
            .withRequiredArg()
            .defaultsTo("jfr");
        ProfilerFactory.configureParser(parser);
        OptionSpecBuilder noDifferentialFlamegraphOption = parser.accepts("no-diffs", "Do not generate differential flame graphs");
        OptionSpecBuilder benchmarkOption = parser.accepts("benchmark", "Collect benchmark metrics");
        ArgumentAcceptingOptionSpec<String> benchmarkBuildOperation = parser.accepts(
            "measure-build-op",
            "Collect benchmark metrics for build operation"
        ).withRequiredArg();
        OptionSpecBuilder noDaemonOption = parser.accepts("no-daemon", "Do not use the Gradle daemon");
        OptionSpecBuilder coldDaemonOption = parser.accepts("cold-daemon", "Use a cold Gradle daemon");
        OptionSpecBuilder cliOption = parser.accepts("cli", "Uses the command-line client to connect to the daemon");
        OptionSpecBuilder measureGarbageCollectionOption = parser.accepts("measure-gc", "Measure the GC time during each invocation");
        OptionSpecBuilder measureConfigTimeOption = parser.accepts("measure-config-time", "Include a breakdown of configuration time in benchmark results");
        OptionSpecBuilder dryRunOption = parser.accepts("dry-run", "Verify configuration");
        OptionSpecBuilder bazelOption = parser.accepts("bazel", "Benchmark scenarios using Bazel");
        OptionSpecBuilder buckOption = parser.accepts("buck", "Benchmark scenarios using buck");
        OptionSpecBuilder mavenOption = parser.accepts("maven", "Benchmark scenarios using Maven");
        ArgumentAcceptingOptionSpec<String> csvFormatOption = parser.accepts("csv-format",
            "The CSV format produced (" + Stream.of(Format.values()).map(Format::toString).collect(Collectors.joining(", ")) + ")")
            .withRequiredArg()
            .defaultsTo("wide");
        ArgumentAcceptingOptionSpec<String> benchmarkTitleOption = parser.accepts("title",
            "Title to show on benchmark report")
            .withOptionalArg()
            .ofType(String.class);

        OptionSet parsedOptions;
        try {
            parsedOptions = parser.parse(args);
        } catch (OptionException e) {
            return fail(parser, e.getMessage());
        }

        if (parsedOptions.has(helpOption)) {
            showHelp(parser);
            return null;
        }

        if (parsedOptions.has(versionOption)) {
            showVersion();
            return null;
        }

        File projectDir = toAbsoluteFileOrNull(parsedOptions.valueOf(projectOption));
        boolean hasProfiler = parsedOptions.has(profilerOption);
        ProfilerFactory profilerFactory = ProfilerFactory.NONE;
        if (hasProfiler) {
            List<String> profilersList = parsedOptions.valuesOf(profilerOption);
            profilerFactory = ProfilerFactory.of(profilersList);
        }
        Profiler profiler = profilerFactory.createFromOptions(parsedOptions);
        boolean generateDiffs = !parsedOptions.has(noDifferentialFlamegraphOption) && hasProfiler && profiler.isCreatesStacksFiles();
        boolean benchmark = parsedOptions.has(benchmarkOption);
        if (!benchmark && !hasProfiler) {
            return fail(parser, "Neither --profile or --benchmark specified.");
        }

        File outputDir = toAbsoluteFileOrNull(parsedOptions.valueOf(outputDirOption));
        File gradleUserHome = toAbsoluteFileOrNull(parsedOptions.valueOf(gradleUserHomeOption));
        Integer warmups = parsedOptions.valueOf(warmupsOption);
        Integer iterations = parsedOptions.valueOf(iterationsOption);
        boolean measureGarbageCollection = parsedOptions.has(measureGarbageCollectionOption);
        boolean measureConfig = parsedOptions.has(measureConfigTimeOption);
        List<String> benchmarkedBuildOperations = parsedOptions.valuesOf(benchmarkBuildOperation);

        List<String> targetNames = parsedOptions.nonOptionArguments().stream().map(Object::toString).collect(Collectors.toList());
        List<String> gradleVersions = parsedOptions.valuesOf(gradleVersionOption);
        File scenarioFile = toAbsoluteFileOrNull(parsedOptions.valueOf(scenarioFileOption));
        File studioInstallDir = toAbsoluteFileOrNull(parsedOptions.valueOf(studioHomeOption));
        File studioSandboxDir = toAbsoluteFileOrNull(parsedOptions.valueOf(studioSandboxOption));
        if (parsedOptions.has(disableStudioSandbox)) {
            studioSandboxDir = null;
        } else if (studioSandboxDir == null) {
            studioSandboxDir = new File(outputDir, "studio-sandbox");
        }

        // TODO - should validate the various combinations of invocation options
        GradleBuildInvoker gradleInvoker = GradleBuildInvoker.ToolingApi;
        if (parsedOptions.has(cliOption)) {
            gradleInvoker = GradleBuildInvoker.Cli;
        }
        if (parsedOptions.has(coldDaemonOption)) {
            gradleInvoker = gradleInvoker.withColdDaemon();
        }
        if (parsedOptions.has(noDaemonOption)) {
            gradleInvoker = GradleBuildInvoker.CliNoDaemon;
        }
        BuildInvoker invoker = gradleInvoker;
        if (parsedOptions.has(bazelOption)) {
            invoker = BuildInvoker.Bazel;
        }
        if (parsedOptions.has(buckOption)) {
            invoker = BuildInvoker.Buck;
        }
        if (parsedOptions.has(mavenOption)) {
            invoker = BuildInvoker.Maven;
        }

        boolean dryRun = parsedOptions.has(dryRunOption);
        Map<String, String> sysProperties = new LinkedHashMap<>();
        for (String value : parsedOptions.valuesOf(sysPropOption)) {
            int sep = value.indexOf("=");
            if (sep < 0) {
                sysProperties.put(value, "true");
            } else {
                sysProperties.put(value.substring(0, sep), value.substring(sep + 1));
            }
        }
        CsvGenerator.Format csvFormat = CsvGenerator.Format.parse(parsedOptions.valueOf(csvFormatOption));
        String benchmarkTitle = parsedOptions.valueOf(benchmarkTitleOption);

        return new InvocationSettings.InvocationSettingsBuilder()
            .setProjectDir(projectDir)
            .setProfiler(profiler)
            .setGenerateDiffs(generateDiffs)
            .setBenchmark(benchmark)
            .setOutputDir(outputDir)
            .setInvoker(invoker)
            .setDryRun(dryRun)
            .setScenarioFile(scenarioFile)
            .setVersions(gradleVersions)
            .setTargets(targetNames)
            .setSysProperties(sysProperties)
            .setGradleUserHome(gradleUserHome)
            .setStudioInstallDir(studioInstallDir)
            .setStudioSandboxDir(studioSandboxDir)
            .setWarmupCount(warmups)
            .setIterations(iterations)
            .setMeasureGarbageCollection(measureGarbageCollection)
            .setMeasureConfigTime(measureConfig)
            .setMeasuredBuildOperations(benchmarkedBuildOperations)
            .setCsvFormat(csvFormat)
            .setBenchmarkTitle(benchmarkTitle)
            .build();
    }

    private File findOutputDir(String path) {
        File outputDir = new File(path);
        if (!outputDir.exists()) {
            return outputDir;
        }
        for (int i = 2; ; i++) {
            outputDir = new File(path + "-" + i);
            if (!outputDir.exists()) {
                return outputDir;
            }
        }
    }

    private InvocationSettings fail(OptionParser parser, String message) throws IOException {
        System.out.println(message);
        System.out.println();
        showHelp(parser);
        throw new SettingsNotAvailableException();
    }

    private void showHelp(OptionParser parser) throws IOException {
        parser.printHelpOn(System.out);
    }

    private void showVersion() {
        System.out.printf("Gradle Profiler version %s%n", CommandLineParser.class.getPackage().getImplementationVersion());
    }

    private File toAbsoluteFileOrNull(@Nullable File file) {
        return file == null ? null : file.getAbsoluteFile();
    }
}

package org.gradle.profiler.bs;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class BuildScanProfiler extends Profiler {

    private final static String VERSION = "1.8";

    private final String buildScanVersion;

    public BuildScanProfiler() {
        this(null);
    }

    private BuildScanProfiler(String buildScanVersion) {
        this.buildScanVersion = buildScanVersion == null ? VERSION : buildScanVersion;
        ;
    }

    @Override
    public String toString() {
        return "buildscan";
    }

    @Override
    public List<String> summarizeResultFile(File resultFile) {
        List<String> buildScanURLs = new ArrayList<>();
        List<String> tasks = new ArrayList<>();
        tasks.add("");
        if (resultFile.getName().equals("profile.log")) {
            try (Stream<String> logStream = Files.lines(resultFile.toPath())) {
                logStream.forEach(line -> {
                    if (line.matches("\\* Running .*\\[.*\\]")) {
                        tasks.clear();
                        tasks.add(line.substring(line.indexOf('[') + 1, line.indexOf(']')));
                    } else if (line.startsWith("Publishing build ")) {
                        buildScanURLs.add("");
                    } else {
                        int lastElementIndex = buildScanURLs.size() - 1;
                        if (lastElementIndex >= 0 && "".equals(buildScanURLs.get(lastElementIndex))) {
                            buildScanURLs.remove(lastElementIndex);
                            buildScanURLs.add("Build Scan [" + tasks.get(0) + "]: " + line);
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return buildScanURLs;
    }

    @Override
    public GradleArgsCalculator newGradleArgsCalculator(ScenarioSettings settings) {
        return new GradleArgsCalculator() {
            @Override
            public void calculateGradleArgs(List<String> gradleArgs) {
                gradleArgs.addAll(new BuildScanInitScript(buildScanVersion).getArgs());
            }
        };
    }

    @Override
    public GradleArgsCalculator newInstrumentedBuildsGradleArgsCalculator(ScenarioSettings settings) {
        return new GradleArgsCalculator() {
            @Override
            public void calculateGradleArgs(List<String> gradleArgs) {
                System.out.println("Using build scan profiler version " + buildScanVersion);
                gradleArgs.add("-Dscan");
            }
        };
    }

    @Override
    public Profiler withConfig(OptionSet parsedOptions) {
        return new BuildScanProfiler((String) parsedOptions.valueOf("buildscan-version"));
    }

    @Override
    public void addOptions(final OptionParser parser) {
        parser.accepts("buildscan-version", "Version of the Build Scan plugin")
                .availableIf("profile")
                .withOptionalArg();
    }
}

package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

public enum AsyncProfilerOutputType {
    STACKS("collapsed", ".stacks.txt"),
    JFR("jfr", ".jfr") {
        @Override
        File outputFileFor(ScenarioSettings settings) {
            return settings.computeJfrProfilerOutputLocation();
        }
    },
    // Generates a flamegraph rendered on an HTML canvas, available since 2.x
    FLAMEGRAPH("flamegraph", ".html"),
    // Other format available: flat, traces, tree
    ;

    public static List<String> allExtensions() {
        return Arrays.stream(values()).map(it -> it.extension).collect(toList());
    }

    public static AsyncProfilerOutputType from(AsyncProfilerConfig config, ScenarioDefinition scenarioDefinition) {
        // TODO autoselect from scenario
        return (config.getEvents().size() > 1 || scenarioDefinition.createsMultipleProcesses())
            ? AsyncProfilerOutputType.JFR
            : AsyncProfilerOutputType.STACKS;
    }

    private final String commandLineOption;
    protected final String extension;

    AsyncProfilerOutputType(String commandLineOption, String extension) {
        this.commandLineOption = commandLineOption;
        this.extension = extension;
    }

    File outputFileFor(ScenarioSettings settings) {
        return settings.profilerOutputLocationFor(extension);
    };

    File individualOutputFileFor(ScenarioSettings settings) {
        File outputFile = outputFileFor(settings);
        return settings.getScenario().createsMultipleProcesses()
            ? new File(outputFile, "profile-%t-%p.jfr") // TODO should it use settings.computeJfrProfilerOutputLocation() instead
            : outputFile;
    }

    public String getCommandLineOption() {
        return commandLineOption;
    }
}

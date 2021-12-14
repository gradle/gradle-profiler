package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;

public enum AsyncProfilerOutputType {
    STACKS("collapsed") {
        @Override
        File outputFileFor(ScenarioSettings settings) {
            return settings.profilerOutputLocationFor(".stacks.txt").getAbsoluteFile();
        }
    },
    JFR("jfr") {
        @Override
        File outputFileFor(ScenarioSettings settings) {
            return settings.computeJfrProfilerOutputLocation().getAbsoluteFile();
        }
    };

    public static AsyncProfilerOutputType from(AsyncProfilerConfig config, GradleScenarioDefinition scenarioDefinition) {
        return (config.getEvents().size() > 1 || scenarioDefinition.createsMultipleProcesses())
            ? AsyncProfilerOutputType.JFR
            : AsyncProfilerOutputType.STACKS;
    }

    private final String commandLineOption;

    AsyncProfilerOutputType(String commandLineOption) {
        this.commandLineOption = commandLineOption;
    }

    abstract File outputFileFor(ScenarioSettings settings);

    File individualOutputFileFor(ScenarioSettings settings) {
        File outputFile = outputFileFor(settings);
        return settings.getScenario().createsMultipleProcesses()
            ? new File(outputFile, "profile-%t-%p.jfr")
            : outputFile;
    }

    public String getCommandLineOption() {
        return commandLineOption;
    }
}

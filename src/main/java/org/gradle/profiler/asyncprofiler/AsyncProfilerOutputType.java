package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.GradleScenarioDefinition;

import java.io.File;

public enum AsyncProfilerOutputType {
    STACKS("collapsed") {
        @Override
        File outputFileFor(GradleScenarioDefinition scenarioDefinition) {
            return AsyncProfiler.stacksFileFor(scenarioDefinition);
        }
    },
    JFR("jfr") {
        @Override
        File outputFileFor(GradleScenarioDefinition scenarioDefinition) {
            return AsyncProfiler.jfrFileFor(scenarioDefinition);
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

    abstract File outputFileFor(GradleScenarioDefinition scenarioDefinition);

    public String getCommandLineOption() {
        return commandLineOption;
    }
}

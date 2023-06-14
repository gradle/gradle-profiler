package org.gradle.profiler;

import java.io.File;

public class ScenarioSettings {
    private static final String PROFILE_JFR_SUFFIX = ".jfr";
    private static final String PROFILE_JFR_DIRECTORY_SUFFIX = "-jfr";

    private final InvocationSettings invocationSettings;
    private final ScenarioDefinition scenario;

    public ScenarioSettings(InvocationSettings invocationSettings, ScenarioDefinition scenario) {
        this.invocationSettings = invocationSettings;
        this.scenario = scenario;
    }

    public InvocationSettings getInvocationSettings() {
        return invocationSettings;
    }

    public ScenarioDefinition getScenario() {
        return scenario;
    }

    public File computeJfrProfilerOutputLocation() {
        if (scenario.createsMultipleProcesses()) {
            File jfrFilesDirectory = profilerOutputLocationFor(PROFILE_JFR_DIRECTORY_SUFFIX);
            jfrFilesDirectory.mkdirs();
            return jfrFilesDirectory;
        } else {
            return profilerOutputLocationFor(PROFILE_JFR_SUFFIX);
        }
    }

    public File profilerOutputLocationFor(String suffix) {
        return new File(getProfilerOutputBaseDir(), getProfilerOutputBaseName() + suffix);
    }

    public File getProfilerOutputBaseDir() {
        return scenario.getOutputDir();
    }

    public String getProfilerOutputBaseName() {
        return scenario.getProfileName();
    }
}

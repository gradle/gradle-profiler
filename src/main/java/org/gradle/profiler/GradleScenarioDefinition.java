package org.gradle.profiler;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class GradleScenarioDefinition extends ScenarioDefinition {

    private final Invoker invoker;
    private final GradleVersion version;
    private final List<String> cleanupTasks;
    private final List<String> tasks;
    private final List<String> gradleArgs;
    private final Map<String, String> systemProperties;

    public GradleScenarioDefinition(String name, Invoker invoker, GradleVersion version, List<String> tasks, List<String> cleanupTasks, List<String> gradleArgs, Map<String, String> systemProperties, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount) {
        super(name, buildMutator, warmUpCount, buildCount);
        this.invoker = invoker;
        this.tasks = tasks;
        this.version = version;
        this.cleanupTasks = cleanupTasks;
        this.gradleArgs = gradleArgs;
        this.systemProperties = systemProperties;
    }

    @Override
    public String getDisplayName() {
        return getName() + " using Gradle " + version.getVersion();
    }

    @Override
    public String getShortDisplayName() {
        return getName() + " " + version.getVersion();
    }

    public List<String> getGradleArgs() {
        return gradleArgs;
    }

    public Invoker getInvoker() {
        return invoker;
    }

    public List<String> getTasks() {
        return tasks;
    }

    public List<String> getCleanupTasks() {
        return cleanupTasks;
    }

    public GradleVersion getVersion() {
        return version;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }
}

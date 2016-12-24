package org.gradle.profiler;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ScenarioDefinition {

    private final String name;
    private final Invoker invoker;
    private final GradleVersion version;
    private final List<String> cleanupTasks;
    private final List<String> tasks;
    private final List<String> gradleArgs;
    private final Map<String, String> systemProperties;
    private final Supplier<BuildMutator> buildMutator;
    private final int warmUpCount;
    private final int buildCount;

    public ScenarioDefinition(String name, Invoker invoker, GradleVersion version, List<String> tasks, List<String> cleanupTasks, List<String> gradleArgs, Map<String, String> systemProperties, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount) {
        this.name = name;
        this.invoker = invoker;
        this.tasks = tasks;
        this.version = version;
        this.cleanupTasks = cleanupTasks;
        this.gradleArgs = gradleArgs;
        this.systemProperties = systemProperties;
        this.buildMutator = buildMutator;
        this.warmUpCount = warmUpCount;
        this.buildCount = buildCount;
    }

    public String getName() {
        return name;
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

    public Supplier<BuildMutator> getBuildMutator() {
        return buildMutator;
    }

    public int getWarmUpCount() {
        return warmUpCount;
    }

    public int getBuildCount() {
        return buildCount;
    }
}

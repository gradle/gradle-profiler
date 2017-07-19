package org.gradle.profiler;

import java.io.File;
import java.io.PrintStream;
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

    public GradleScenarioDefinition(String name, Invoker invoker, GradleVersion version, List<String> tasks, List<String> cleanupTasks, List<String> gradleArgs, Map<String, String> systemProperties, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount, File outputDir) {
        super(name, buildMutator, warmUpCount, buildCount, outputDir);
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

    @Override
    public String getProfileName() {
        return getName() + "-" + version.getVersion();
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

    @Override
    protected void printDetail(PrintStream out) {
        out.println("  Gradle version: " + getVersion().getVersion() + " (" + getVersion().getGradleHome() + ")");
        out.println("  Run using: " + getInvoker());
        out.println("  Cleanup Tasks: " + getCleanupTasks());
        out.println("  Tasks: " + getTasks());
        out.println("  Gradle args: " + getGradleArgs());
        if (!getSystemProperties().isEmpty()) {
            out.println("  System properties:");
            for (Map.Entry<String, String> entry : getSystemProperties().entrySet()) {
                out.println("    " + entry.getKey() + "=" + entry.getValue());
            }
        }
    }
}

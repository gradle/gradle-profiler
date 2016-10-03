package net.rubygrapefruit.gradle.profiler;

import java.util.List;
import java.util.Map;

class ScenarioDefinition {

    private final String name;
    private final Invoker invoker;
    private final List<GradleVersion> versions;
    private final List<String> tasks;
    private final Map<String, String> systemProperties;

    public ScenarioDefinition(String name, Invoker invoker, List<GradleVersion> versions, List<String> tasks, Map<String, String> systemProperties) {
        this.name = name;
        this.invoker = invoker;
        this.tasks = tasks;
        this.versions = versions;
        this.systemProperties = systemProperties;
    }

    public String getName() {
        return name;
    }

    public Invoker getInvoker() {
        return invoker;
    }

    public List<String> getTasks() {
        return tasks;
    }

    public List<GradleVersion> getVersions() {
        return versions;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }
}

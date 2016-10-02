package net.rubygrapefruit.gradle.profiler;

import java.util.List;

class ScenarioDefinition {

    private final String name;
    private final Invoker invoker;
    private final List<GradleVersion> versions;
    private final List<String> tasks;

    public ScenarioDefinition(String name, Invoker invoker, List<GradleVersion> versions, List<String> tasks) {
        this.name = name;
        this.invoker = invoker;
        this.tasks = tasks;
        this.versions = versions;
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
}

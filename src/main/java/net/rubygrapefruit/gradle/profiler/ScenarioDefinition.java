package net.rubygrapefruit.gradle.profiler;

import java.util.List;

class ScenarioDefinition {
    private final String name;
    private final List<GradleVersion> versions;
    private final List<String> tasks;

    public ScenarioDefinition(String name, List<GradleVersion> versions, List<String> tasks) {
        this.name = name;
        this.tasks = tasks;
        this.versions = versions;
    }

    public String getName() {
        return name;
    }

    public List<String> getTasks() {
        return tasks;
    }

    public List<GradleVersion> getVersions() {
        return versions;
    }
}

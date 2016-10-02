package net.rubygrapefruit.gradle.profiler;

import java.io.File;
import java.util.List;

class InvocationSettings {
    private final File projectDir;
    private final boolean profile;
    private final boolean benchmark;
    private final List<String> versions;
    private final List<String> tasks;

    public InvocationSettings(File projectDir, boolean profile, boolean benchmark, List<String> versions, List<String> tasks) {
        this.benchmark = benchmark;
        this.projectDir = projectDir;
        this.profile = profile;
        this.versions = versions;
        this.tasks = tasks;
    }

    public boolean isBenchmark() {
        return benchmark;
    }

    public boolean isProfile() {
        return profile;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public List<String> getVersions() {
        return versions;
    }

    public List<String> getTasks() {
        return tasks;
    }

    public int getBuildCount() {
        return benchmark ? 13 : 1;
    }
}

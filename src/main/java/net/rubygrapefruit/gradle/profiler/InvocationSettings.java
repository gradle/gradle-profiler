package net.rubygrapefruit.gradle.profiler;

import java.io.File;
import java.util.List;

class InvocationSettings {
    private final File projectDir;
    private final boolean profile;
    private final boolean benchmark;
    private final List<String> tasks;

    public InvocationSettings(File projectDir, boolean profile, boolean benchmark, List<String> tasks) {
        this.benchmark = benchmark;
        this.projectDir = projectDir;
        this.profile = profile;
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

    public List<String> getTasks() {
        return tasks;
    }
}

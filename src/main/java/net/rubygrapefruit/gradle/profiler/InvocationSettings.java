package net.rubygrapefruit.gradle.profiler;

import java.io.File;
import java.util.List;
import java.util.Map;

class InvocationSettings {
    private final File projectDir;
    private final boolean profile;
    private final boolean benchmark;
    private final boolean dryRun;
    private final File configFile;
    private final File outputDir;
    private final Invoker invoker;
    private final List<String> versions;
    private final List<String> tasks;
    private final Map<String, String> sysProperties;

    public InvocationSettings(File projectDir, boolean profile, boolean benchmark, File outputDir, Invoker invoker, boolean dryRun, File configFile, List<String> versions, List<String> tasks, Map<String, String> sysProperties) {
        this.benchmark = benchmark;
        this.projectDir = projectDir;
        this.profile = profile;
        this.outputDir = outputDir;
        this.invoker = invoker;
        this.dryRun = dryRun;
        this.configFile = configFile;
        this.versions = versions;
        this.tasks = tasks;
        this.sysProperties = sysProperties;
    }

    public File getOutputDir() {
        return outputDir;
    }

    public Invoker getInvoker() {
        return invoker;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isBenchmark() {
        return benchmark;
    }

    public boolean isProfile() {
        return profile;
    }

    public File getConfigFile() {
        return configFile;
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

    public Map<String, String> getSystemProperties() {
        return sysProperties;
    }

    public int getWarmUpCount() {
        return dryRun ? 1 : 2;
    }

    public int getBuildCount() {
        return (benchmark && !dryRun) ? 13 : 1;
    }
}

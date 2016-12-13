package org.gradle.profiler;

import java.io.File;
import java.util.List;
import java.util.Map;

public class InvocationSettings {
    private final File projectDir;
    private final Profiler profiler;
    private final Object profilerOptions;
    private final boolean benchmark;
    private final boolean dryRun;
    private final File scenarioFile;
    private final File outputDir;
    private final Invoker invoker;
    private final List<String> versions;
    private final List<String> tasks;
    private final Map<String, String> sysProperties;

    public InvocationSettings(File projectDir, Profiler profiler, final Object profilerOptions, boolean benchmark, File outputDir, Invoker invoker, boolean dryRun, File scenarioFile, List<String> versions, List<String> tasks, Map<String, String> sysProperties) {
        this.profilerOptions = profilerOptions;
        this.benchmark = benchmark;
        this.projectDir = projectDir;
        this.profiler = profiler;
        this.outputDir = outputDir;
        this.invoker = invoker;
        this.dryRun = dryRun;
        this.scenarioFile = scenarioFile;
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
        return profiler != Profiler.none;
    }

    public Profiler getProfiler() {
        return profiler;
    }

    public Object getProfilerOptions() {
        return profilerOptions;
    }

    public File getScenarioFile() {
        return scenarioFile;
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

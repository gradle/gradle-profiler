package org.gradle.profiler;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

public class InvocationSettings {
    private final File projectDir;
    private final Profiler profiler;
    private final boolean benchmark;
    private final boolean dryRun;
    private final File scenarioFile;
    private final File outputDir;
    private final BuildInvoker invoker;
    private final List<String> versions;
    private final List<String> targets;
    private final Map<String, String> sysProperties;
    private final File gradleUserHome;
    private final Integer warmupCount;
    private final Integer iterations;
    private final boolean measureConfigTime;
    private final ImmutableList<String> measuredBuildOperations;

    public InvocationSettings(
        File projectDir,
        Profiler profiler,
        boolean benchmark,
        File outputDir,
        BuildInvoker invoker,
        boolean dryRun,
        File scenarioFile,
        List<String> versions,
        List<String> getTargets,
        Map<String,
        String> sysProperties,
        File gradleUserHome,
        Integer warmupCount,
        Integer iterations,
        boolean measureConfigTime,
        List<String> measuredBuildOperations
    ) {
        this.benchmark = benchmark;
        this.projectDir = projectDir;
        this.profiler = profiler;
        this.outputDir = outputDir;
        this.invoker = invoker;
        this.dryRun = dryRun;
        this.scenarioFile = scenarioFile;
        this.versions = versions;
        this.targets = getTargets;
        this.sysProperties = sysProperties;
        this.gradleUserHome = gradleUserHome;
        this.warmupCount = warmupCount;
        this.iterations = iterations;
        this.measureConfigTime = measureConfigTime;
        this.measuredBuildOperations = ImmutableList.copyOf(measuredBuildOperations);
    }

    public File getOutputDir() {
        return outputDir;
    }

    public BuildInvoker getInvoker() {
        return invoker;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isBenchmark() {
        return benchmark;
    }

    public boolean isProfile() {
        return profiler != Profiler.NONE;
    }

    public boolean isBazel() {
        return invoker == BuildInvoker.Bazel;
    }

    public boolean isBuck() {
        return invoker == BuildInvoker.Buck;
    }

    public boolean isMaven() {
        return invoker == BuildInvoker.Maven;
    }

    public Profiler getProfiler() {
        return profiler;
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

    public List<String> getTargets() {
        return targets;
    }

    public Map<String, String> getSystemProperties() {
        return sysProperties;
    }

    public Integer getWarmUpCount() {
        return warmupCount;
    }

    public Integer getBuildCount() {
        return iterations;
    }

    public File getGradleUserHome() {
        return gradleUserHome;
    }

    public boolean isMeasureConfigTime() {
        return measureConfigTime;
    }

    public ImmutableList<String> getMeasuredBuildOperations() {
        return measuredBuildOperations;
    }

    public void printTo(PrintStream out) {
        out.println("Project dir: " + getProjectDir());
        out.println("Output dir: " + getOutputDir());
        out.println("Profiler: " + getProfiler());
        out.println("Benchmark: " + isBenchmark());
        out.println("Versions: " + getVersions());
        out.println("Gradle User Home: " + getGradleUserHome());
        out.println("Targets: " + getTargets());
        if (warmupCount != null) {
            out.println("Warm-ups: " + warmupCount);
        }
        if (iterations != null) {
            out.println("Builds: " + iterations);
        }
        if (!getSystemProperties().isEmpty()) {
            out.println("System properties:");
            for (Map.Entry<String, String> entry : getSystemProperties().entrySet()) {
                out.println("  " + entry.getKey() + "=" + entry.getValue());
            }
        }
    }
}

package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gradle.profiler.report.CsvGenerator.Format;

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
    private final File studioInstallDir;
    private final Integer warmupCount;
    private final Integer iterations;
    private final boolean measureConfigTime;
    private final List<String> measuredBuildOperations;
    private final Format csvFormat;
    /**
     * The log file which the build should write stdout and stderr to.
     * If {@code null}, the stdout and stderr are stored in memory.
     */
    private final File buildLog;

    private final UUID invocationId = UUID.randomUUID();

    private InvocationSettings(
        File projectDir,
        Profiler profiler,
        boolean benchmark,
        File outputDir,
        BuildInvoker invoker,
        boolean dryRun,
        File scenarioFile,
        List<String> versions,
        List<String> targets,
        Map<String, String> sysProperties,
        File gradleUserHome,
        File studioInstallDir,
        Integer warmupCount,
        Integer iterations,
        boolean measureConfigTime,
        List<String> measuredBuildOperations,
        Format csvFormat,
        File buildLog
    ) {
        this.benchmark = benchmark;
        this.projectDir = projectDir;
        this.profiler = profiler;
        this.outputDir = outputDir;
        this.invoker = invoker;
        this.dryRun = dryRun;
        this.scenarioFile = scenarioFile;
        this.versions = versions;
        this.targets = targets;
        this.sysProperties = sysProperties;
        this.gradleUserHome = gradleUserHome;
        this.studioInstallDir = studioInstallDir;
        this.warmupCount = warmupCount;
        this.iterations = iterations;
        this.measureConfigTime = measureConfigTime;
        this.measuredBuildOperations = measuredBuildOperations;
        this.csvFormat = csvFormat;
        this.buildLog = buildLog;
    }

    @Nullable
    public File getBuildLog() {
        return buildLog;
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

    public File getStudioInstallDir() {
        return studioInstallDir;
    }

    public boolean isMeasureConfigTime() {
        return measureConfigTime;
    }

    public List<String> getMeasuredBuildOperations() {
        return measuredBuildOperations;
    }

    public Format getCsvFormat() {
        return csvFormat;
    }

    public UUID getInvocationId() {
        return invocationId;
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

    public static final class InvocationSettingsBuilder {
        private File projectDir;
        private Profiler profiler;
        private boolean benchmark;
        private boolean dryRun;
        private File scenarioFile;
        private File outputDir;
        private BuildInvoker invoker;
        private List<String> versions;
        private List<String> targets;
        private Map<String, String> sysProperties;
        private File gradleUserHome;
        private File studioInstallDir;
        private Integer warmupCount;
        private Integer iterations;
        private boolean measureConfigTime;
        private List<String> measuredBuildOperations;
        private Format csvFormat;
        private File buildLog;

        public InvocationSettingsBuilder setProjectDir(File projectDir) {
            this.projectDir = projectDir;
            return this;
        }

        public InvocationSettingsBuilder setProfiler(Profiler profiler) {
            this.profiler = profiler;
            return this;
        }

        public InvocationSettingsBuilder setBenchmark(boolean benchmark) {
            this.benchmark = benchmark;
            return this;
        }

        public InvocationSettingsBuilder setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public InvocationSettingsBuilder setScenarioFile(File scenarioFile) {
            this.scenarioFile = scenarioFile;
            return this;
        }

        public InvocationSettingsBuilder setOutputDir(File outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public InvocationSettingsBuilder setInvoker(BuildInvoker invoker) {
            this.invoker = invoker;
            return this;
        }

        public InvocationSettingsBuilder setVersions(List<String> versions) {
            this.versions = versions;
            return this;
        }

        public InvocationSettingsBuilder setTargets(List<String> targets) {
            this.targets = targets;
            return this;
        }

        public InvocationSettingsBuilder setSysProperties(Map<String, String> sysProperties) {
            this.sysProperties = sysProperties;
            return this;
        }

        public InvocationSettingsBuilder setGradleUserHome(File gradleUserHome) {
            this.gradleUserHome = gradleUserHome;
            return this;
        }

        public InvocationSettingsBuilder setStudioInstallDir(File studioInstallDir) {
            this.studioInstallDir = studioInstallDir;
            return this;
        }

        public InvocationSettingsBuilder setWarmupCount(Integer warmupCount) {
            this.warmupCount = warmupCount;
            return this;
        }

        public InvocationSettingsBuilder setIterations(Integer iterations) {
            this.iterations = iterations;
            return this;
        }

        public InvocationSettingsBuilder setMeasureConfigTime(boolean measureConfigTime) {
            this.measureConfigTime = measureConfigTime;
            return this;
        }

        public InvocationSettingsBuilder setMeasuredBuildOperations(List<String> measuredBuildOperations) {
            this.measuredBuildOperations = measuredBuildOperations;
            return this;
        }

        public InvocationSettingsBuilder setCsvFormat(Format csvFormat) {
            this.csvFormat = csvFormat;
            return this;
        }

        public InvocationSettingsBuilder setBuildLog(File buildLog) {
            this.buildLog = buildLog;
            return this;
        }

        public InvocationSettings build() {
            return new InvocationSettings(
                projectDir,
                profiler,
                benchmark,
                outputDir,
                invoker,
                dryRun,
                scenarioFile,
                versions,
                targets,
                sysProperties,
                gradleUserHome,
                studioInstallDir,
                warmupCount,
                iterations,
                measureConfigTime,
                measuredBuildOperations,
                csvFormat,
                buildLog
            );
        }
    }
}

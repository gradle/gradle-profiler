package org.gradle.profiler;

import org.gradle.profiler.idea.IdeaSyncInvocationSettings;
import org.gradle.profiler.report.Format;

import javax.annotation.Nullable;
import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InvocationSettings {
    private final File projectDir;
    private final Profiler profiler;
    private final boolean generateDiffs;
    private final boolean benchmark;
    private final boolean dryRun;
    private final boolean dumpScenarios;
    private final File scenarioFile;
    private final File outputDir;
    private final BuildInvoker invoker;
    private final List<String> versions;
    private final List<String> targets;
    private final Map<String, String> sysProperties;
    private final File gradleUserHome;
    private final File studioInstallDir;
    private final File studioSandboxDir;

    @Nullable
    private final IdeaSyncInvocationSettings ideaSyncInvocationSettings;

    private final Integer warmupCount;
    private final Integer iterations;
    private final boolean measureGarbageCollection;
    private final boolean measureLocalBuildCache;
    private final boolean measureConfigTime;
    private final List<String> measuredBuildOperations;
    private final boolean buildOperationsTrace;
    private final Format csvFormat;
    private final String benchmarkTitle;
    private final String scenarioGroup;
    /**
     * The log file which the build should write stdout and stderr to.
     * If {@code null}, the stdout and stderr are stored in memory.
     */
    private final File buildLog;

    private final UUID invocationId = UUID.randomUUID();

    private InvocationSettings(
        File projectDir,
        Profiler profiler,
        boolean generateDiffs,
        boolean benchmark,
        File outputDir,
        BuildInvoker invoker,
        boolean dryRun,
        boolean dumpScenarios,
        File scenarioFile,
        List<String> versions,
        List<String> targets,
        Map<String, String> sysProperties,
        File gradleUserHome,
        @Nullable File studioInstallDir,
        File studioSandboxDir,
        Integer warmupCount,
        Integer iterations,
        boolean measureGarbageCollection,
        boolean measureLocalBuildCache,
        boolean measureConfigTime,
        List<String> measuredBuildOperations,
        boolean buildOperationsTrace,
        Format csvFormat,
        String benchmarkTitle,
        String scenarioGroup,
        File buildLog,
        @Nullable IdeaSyncInvocationSettings ideaSyncInvocationSettings
    ) {
        this.benchmark = benchmark;
        this.projectDir = projectDir;
        this.profiler = profiler;
        this.generateDiffs = generateDiffs;
        this.outputDir = outputDir;
        this.invoker = invoker;
        this.dryRun = dryRun;
        this.dumpScenarios = dumpScenarios;
        this.scenarioFile = scenarioFile;
        this.versions = versions;
        this.targets = targets;
        this.sysProperties = sysProperties;
        this.gradleUserHome = gradleUserHome;
        this.studioInstallDir = studioInstallDir;
        this.studioSandboxDir = studioSandboxDir;
        this.warmupCount = warmupCount;
        this.iterations = iterations;
        this.measureGarbageCollection = measureGarbageCollection;
        this.measureLocalBuildCache = measureLocalBuildCache;
        this.measureConfigTime = measureConfigTime;
        this.measuredBuildOperations = measuredBuildOperations;
        this.buildOperationsTrace = buildOperationsTrace;
        this.csvFormat = csvFormat;
        this.benchmarkTitle = benchmarkTitle;
        this.scenarioGroup = scenarioGroup;
        this.buildLog = buildLog;
        this.ideaSyncInvocationSettings = ideaSyncInvocationSettings;
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

    public boolean isDumpScenarios() {
        return dumpScenarios;
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

    public boolean isGenerateDiffs() {
        return generateDiffs;
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

    public Optional<File> getStudioSandboxDir() {
        return Optional.ofNullable(studioSandboxDir);
    }

    public boolean isMeasureGarbageCollection() {
        return measureGarbageCollection;
    }

    public boolean isMeasureLocalBuildCache() {
        return measureLocalBuildCache;
    }

    public boolean isMeasureConfigTime() {
        return measureConfigTime;
    }

    public List<String> getMeasuredBuildOperations() {
        return measuredBuildOperations;
    }

    public boolean isBuildOperationsTrace() {
        return buildOperationsTrace;
    }

    public Format getCsvFormat() {
        return csvFormat;
    }

    /**
     * The title of the benchmark. Shown on the HTML report and on the console.
     */
    public String getBenchmarkTitle() {
        return benchmarkTitle;
    }

    /**
     * The name of the scenario group to run, if specified via --group.
     * When set, only scenarios from this group will be executed.
     *
     * @return the scenario group name, or null if not specified
     */
    @Nullable
    public String getScenarioGroup() {
        return scenarioGroup;
    }

    public UUID getInvocationId() {
        return invocationId;
    }

    public IdeaSyncInvocationSettings getIdeaSyncInvocationSettings() {
        return ideaSyncInvocationSettings;
    }

    public InvocationSettingsBuilder newBuilder() {
        return new InvocationSettings.InvocationSettingsBuilder()
            .setProjectDir(projectDir)
            .setProfiler(profiler)
            .setGenerateDiffs(generateDiffs)
            .setBenchmark(benchmark)
            .setDryRun(dryRun)
            .setDumpScenarios(dumpScenarios)
            .setScenarioFile(scenarioFile)
            .setOutputDir(outputDir)
            .setInvoker(invoker)
            .setVersions(versions)
            .setTargets(targets)
            .setSysProperties(sysProperties)
            .setGradleUserHome(gradleUserHome)
            .setStudioInstallDir(studioInstallDir)
            .setWarmupCount(warmupCount)
            .setIterations(iterations)
            .setMeasureGarbageCollection(measureGarbageCollection)
            .setMeasureConfigTime(measureConfigTime)
            .setMeasuredBuildOperations(measuredBuildOperations)
            .setBuildOperationsTrace(buildOperationsTrace)
            .setCsvFormat(csvFormat)
            .setBenchmarkTitle(benchmarkTitle)
            .setScenarioGroup(scenarioGroup)
            .setBuildLog(buildLog)
            .setIdeaSyncInvocationSettings(ideaSyncInvocationSettings);
    }

    public void printTo(PrintStream out) {
        if (benchmarkTitle != null) {
            out.println("Title: " + benchmarkTitle);
        }
        out.println("Project dir: " + getProjectDir());
        out.println("Output dir: " + getOutputDir());
        out.println("Profiler: " + getProfiler());
        out.println("Benchmark: " + isBenchmark());
        out.println("Versions: " + getVersions());
        out.println("Gradle User Home: " + getGradleUserHome());
        if (getScenarioGroup() != null) {
            out.println("Targets: '" + getScenarioGroup() + "' (group)");
        } else if (getTargets() != null && !getTargets().isEmpty()) {
            out.println("Targets: " + getTargets());
        } else {
            out.println("Targets: default scenarios");
        }
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
        private boolean generateDiffs;
        private boolean benchmark;
        private boolean dryRun;
        private boolean dumpScenarios;
        private File scenarioFile;
        private File outputDir;
        private BuildInvoker invoker;
        private List<String> versions;
        private List<String> targets;
        private Map<String, String> sysProperties;
        private File gradleUserHome;
        private File studioInstallDir;
        private File studioSandboxDir;
        private Integer warmupCount;
        private Integer iterations;
        private boolean measureGarbageCollection;
        private boolean measureLocalBuildCache;
        private boolean measureConfigTime;
        private List<String> measuredBuildOperations;
        private boolean buildOperationsTrace;
        private Format csvFormat;
        private String benchmarkTitle;
        private String scenarioGroup;
        private File buildLog;
        @Nullable
        private IdeaSyncInvocationSettings ideaSyncInvocationSettings;

        public InvocationSettingsBuilder setIdeaSyncInvocationSettings(IdeaSyncInvocationSettings ideaSyncInvocationSettings) {
            this.ideaSyncInvocationSettings = ideaSyncInvocationSettings;
            return this;
        }

        public InvocationSettingsBuilder setProjectDir(File projectDir) {
            this.projectDir = projectDir;
            return this;
        }

        public InvocationSettingsBuilder setProfiler(Profiler profiler) {
            this.profiler = profiler;
            return this;
        }

        public InvocationSettingsBuilder setGenerateDiffs(boolean generateDiffs) {
            this.generateDiffs = generateDiffs;
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

        public InvocationSettingsBuilder setDumpScenarios(boolean dumpScenarios) {
            this.dumpScenarios = dumpScenarios;
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

        public InvocationSettingsBuilder setStudioSandboxDir(@Nullable File studioSandboxDir) {
            this.studioSandboxDir = studioSandboxDir;
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

        public InvocationSettingsBuilder setMeasureGarbageCollection(boolean measureGarbageCollection) {
            this.measureGarbageCollection = measureGarbageCollection;
            return this;
        }

        public InvocationSettingsBuilder setMeasureLocalBuildCache(boolean measureLocalBuildCache) {
            this.measureLocalBuildCache = measureLocalBuildCache;
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

        public InvocationSettingsBuilder setBuildOperationsTrace(boolean buildOperationsTrace) {
            this.buildOperationsTrace = buildOperationsTrace;
            return this;
        }

        public InvocationSettingsBuilder setCsvFormat(Format csvFormat) {
            this.csvFormat = csvFormat;
            return this;
        }

        /**
         * The title of the benchmark. Shown on the HTML report and on the console.
         */
        public InvocationSettingsBuilder setBenchmarkTitle(@Nullable String benchmarkTitle) {
            this.benchmarkTitle = benchmarkTitle;
            return this;
        }

        /**
         * Sets the scenario group to run. When set, only scenarios from this group will be executed.
         * Cannot be combined with individual scenario names.
         *
         * @param scenarioGroup the scenario group name, or null
         * @return this builder
         */
        public InvocationSettingsBuilder setScenarioGroup(@Nullable String scenarioGroup) {
            this.scenarioGroup = scenarioGroup;
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
                generateDiffs,
                benchmark,
                outputDir,
                invoker,
                dryRun,
                dumpScenarios,
                scenarioFile,
                versions,
                targets,
                sysProperties,
                gradleUserHome,
                studioInstallDir,
                studioSandboxDir,
                warmupCount,
                iterations,
                measureGarbageCollection,
                measureLocalBuildCache,
                measureConfigTime,
                measuredBuildOperations,
                buildOperationsTrace,
                csvFormat,
                benchmarkTitle,
                scenarioGroup,
                buildLog,
                ideaSyncInvocationSettings
            );
        }
    }
}

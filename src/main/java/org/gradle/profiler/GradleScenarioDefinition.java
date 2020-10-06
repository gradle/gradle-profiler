package org.gradle.profiler;

import org.gradle.profiler.buildops.BuildOperationUtil;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GradleScenarioDefinition extends ScenarioDefinition {

    private final GradleBuildInvoker invoker;
    private final GradleBuildConfiguration buildConfiguration;
    private final BuildAction buildAction;
    private final BuildAction cleanupAction;
    private final List<String> gradleArgs;
    private final Map<String, String> systemProperties;
    private final List<String> jvmArgs;
    private final List<String> measuredBuildOperations;

    public GradleScenarioDefinition(
        String name,
        String title,
        GradleBuildInvoker invoker,
        GradleBuildConfiguration buildConfiguration,
        BuildAction buildAction,
        BuildAction cleanupAction,
        List<String> gradleArgs,
        Map<String, String> systemProperties,
        List<BuildMutator> buildMutators,
        int warmUpCount,
        int buildCount,
        File outputDir,
        List<String> jvmArgs,
        List<String> measuredBuildOperations
    ) {
        super(name, title, buildMutators, warmUpCount, buildCount, outputDir);
        this.invoker = invoker;
        this.buildAction = buildAction;
        this.buildConfiguration = buildConfiguration;
        this.cleanupAction = cleanupAction;
        this.gradleArgs = gradleArgs;
        this.systemProperties = systemProperties;
        this.jvmArgs = jvmArgs;
        this.measuredBuildOperations = measuredBuildOperations;
    }

    @Override
    public String getDisplayName() {
        return getTitle() + " using " + buildConfiguration.getGradleVersion();
    }

    @Override
    public String getProfileName() {
        return safeFileName(getName()) + "-" + buildConfiguration.getGradleVersion().getVersion();
    }

    public static String safeFileName(String name) {
        return name.replace("/", "-");
    }

    @Override
    public String getBuildToolDisplayName() {
        return buildConfiguration.getGradleVersion().toString();
    }

    @Override
    public String getTasksDisplayName() {
        return buildAction.getShortDisplayName();
    }

    public List<String> getGradleArgs() {
        return gradleArgs;
    }

    @Override
    public GradleBuildInvoker getInvoker() {
        return invoker;
    }

    public BuildAction getAction() {
        return buildAction;
    }

    public BuildAction getCleanupAction() {
        return cleanupAction;
    }

    public GradleBuildConfiguration getBuildConfiguration() {
        return buildConfiguration;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    public List<String> getMeasuredBuildOperations() {
        return measuredBuildOperations;
    }

    @Override
    public void visitProblems(InvocationSettings settings, Consumer<String> reporter) {
        if (getWarmUpCount() < 1) {
            reporter.accept("You can not skip warm-ups when profiling or benchmarking a Gradle build. Use --no-daemon or --cold-daemon if you want to profile or benchmark JVM startup");
        }
        if (settings.isMeasureGarbageCollection() && isBuildServiceUnsupported()) {
            reporter.accept("Measuring garbage collection is only supported for Gradle 6.1-milestone-3 and later");
        }
        if (settings.isMeasureConfigTime() && isBuildServiceUnsupported()) {
            reporter.accept("Measuring build configuration is only supported for Gradle 6.1-milestone-3 and later");
        }
        settings.getProfiler().validate(new ScenarioSettings(settings, this), reporter);
    }

    private boolean isBuildServiceUnsupported() {
        return buildConfiguration.getGradleVersion().compareTo(GradleVersion.version("6.1-milestone-3")) < 0;
    }

    @Override
    protected void printDetail(PrintStream out) {
        out.println("  " + getBuildConfiguration().getGradleVersion() + " (" + getBuildConfiguration().getGradleHome() + ")");
        out.println("  Run using: " + getInvoker());
        out.println("  Run: " + getAction().getDisplayName());
        out.println("  Cleanup: " + getCleanupAction().getDisplayName());
        out.println("  Gradle args: " + getGradleArgs());
        if (!getSystemProperties().isEmpty()) {
            out.println("  System properties:");
            for (Map.Entry<String, String> entry : getSystemProperties().entrySet()) {
                out.println("    " + entry.getKey() + "=" + entry.getValue());
            }
        }
        if (!jvmArgs.isEmpty()) {
            out.println("  Jvm args: " + getJvmArgs());
        }
        if (!measuredBuildOperations.isEmpty()) {
            out.println("  Measured build operations: " + measuredBuildOperations.stream()
                .map(BuildOperationUtil::getSimpleBuildOperationName)
                .sorted()
                .collect(Collectors.joining(", ")));
        }
    }
}

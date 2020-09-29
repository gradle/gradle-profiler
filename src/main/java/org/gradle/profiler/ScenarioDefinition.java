package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.File;
import java.io.PrintStream;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class ScenarioDefinition {
    private final String name;
    private final String title;
    private final BuildMutator buildMutator;
    private final int warmUpCount;
    private final int buildCount;
    private final File outputDir;


    public ScenarioDefinition(
        String name,
        @Nullable String title,
        Supplier<BuildMutator> buildMutator,
        int warmUpCount,
        int buildCount,
        File outputDir
    ) {
        this.name = name;
        this.title = title;
        this.buildMutator = buildMutator.get();
        this.warmUpCount = warmUpCount;
        this.buildCount = buildCount;
        this.outputDir = outputDir;
    }

    /**
     * A specific title defined for the scenario to be used in reports (defaults to {@link #getName()}.
     */
    public String getTitle() {
        return title != null ? title : name;
    }

    /**
     * A human consumable and unique display name for this scenario using {@link #getTitle()}.
     */
    public abstract String getDisplayName();

    /**
     * A unique name for this scenario, that can be used for file names and other identifiers.
     */
    public abstract String getProfileName();

    /**
     * A human consumable description of the build tool that runs this scenario.
     */
    public abstract String getBuildToolDisplayName();

    /**
     * A human consumable description of the 'tasks' that are run for this scenario (may not be Gradle tasks).
     */
    public abstract String getTasksDisplayName();

    public String getName() {
        return name;
    }

    public File getOutputDir() {
        return outputDir;
    }

    public BuildMutator getBuildMutator() {
        return buildMutator;
    }

    public int getWarmUpCount() {
        return warmUpCount;
    }

    public int getBuildCount() {
        return buildCount;
    }

    public void printTo(PrintStream out) {
        out.println("Scenario: " + getDisplayName());
        printDetail(out);
        out.println("  Build changes: " + getBuildMutator());
        out.println("  Warm-ups: " + getWarmUpCount());
        out.println("  Builds: " + getBuildCount());
    }

    public void visitProblems(InvocationSettings settings, Consumer<String> reporter) {
    }

    protected void printDetail(PrintStream out) {
    }
}

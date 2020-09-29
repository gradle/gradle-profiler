package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Consumer;

public abstract class ScenarioDefinition {
    private final String name;
    private final String title;
    private final List<BuildMutator> buildMutators;
    private final int warmUpCount;
    private final int buildCount;
    private final File outputDir;

    public ScenarioDefinition(
        String name,
        @Nullable String title,
        List<BuildMutator> buildMutators,
        int warmUpCount,
        int buildCount,
        File outputDir
    ) {
        this.name = name;
        this.title = title;
        this.buildMutators = buildMutators;
        this.warmUpCount = warmUpCount;
        this.buildCount = buildCount;
        this.outputDir = outputDir;
    }

    public void validate() {
        for (BuildMutator buildMutator : buildMutators) {
            try {
                buildMutator.validate(getInvoker());
            } catch (Exception ex) {
                throw new IllegalStateException("Scenario '" + getTitle() + "' is invalid: " + ex.getMessage(), ex);
            }
        }
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

    public abstract BuildInvoker getInvoker();

    public String getName() {
        return name;
    }

    public File getOutputDir() {
        return outputDir;
    }

    public List<BuildMutator> getBuildMutators() {
        return buildMutators;
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
        out.println("  Build changes: " + getBuildMutators());
        out.println("  Warm-ups: " + getWarmUpCount());
        out.println("  Builds: " + getBuildCount());
    }

    public void visitProblems(InvocationSettings settings, Consumer<String> reporter) {
    }

    protected void printDetail(PrintStream out) {
    }
}

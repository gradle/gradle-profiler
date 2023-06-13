package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.File;
import java.io.PrintStream;
import java.util.List;

public abstract class BuildToolCommandLineScenarioDefinition extends ScenarioDefinition {
    private final List<String> targets;
    private final File toolHome;

    public BuildToolCommandLineScenarioDefinition(
        String name,
        @Nullable String title,
        List<String> targets,
        List<BuildMutator> buildMutators,
        int warmUpCount,
        int buildCount,
        File outputDir,
        @Nullable File toolHome
    ) {
        super(name, title, buildMutators, warmUpCount, buildCount, outputDir);
        this.targets = targets;
        this.toolHome = toolHome;
    }

    protected abstract String getExecutableName();

    protected abstract String getToolHomeEnvName();

    @Override
    protected void printDetail(PrintStream out) {
        out.println("  Targets: " + getTargets());
    }

    public String getExecutablePath() {
        String toolHomePath = getToolHome() == null ? System.getenv(getToolHomeEnvName()) : getToolHome().getAbsolutePath();
        return toolHomePath == null ? getExecutableName() : toolHomePath + "/bin/" + getExecutableName();
    }

    @Override
    public String getTasksDisplayName() {
        return String.join(" ", targets);
    }

    public List<String> getTargets() {
        return targets;
    }

    public File getToolHome() {
        return toolHome;
    }
}

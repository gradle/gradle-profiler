package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class BuildToolCommandLineScenarioDefinition extends ScenarioDefinition {
    private final List<String> targets;
    private final File toolHome;

    public BuildToolCommandLineScenarioDefinition(
        String name,
        @Nullable String title,
        List<String> targets,
        Supplier<BuildMutator> buildMutator,
        int warmUpCount,
        int buildCount,
        File outputDir,
        @Nullable File toolHome
    ) {
        super(name, title, buildMutator, warmUpCount, buildCount, outputDir);
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
        String toolHomePath = toolHome == null ? System.getenv(getToolHomeEnvName()) : toolHome.getAbsolutePath();
        return toolHomePath == null ? getExecutableName() : toolHomePath + "/bin/" + getExecutableName();
    }

    @Override
    public String getTasksDisplayName() {
        return targets.stream().collect(Collectors.joining(" "));
    }

    public List<String> getTargets() {
        return targets;
    }

    public File getToolHome() {
        return toolHome;
    }
}

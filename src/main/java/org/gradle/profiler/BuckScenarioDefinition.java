package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.File;
import java.io.PrintStream;
import java.util.List;

public class BuckScenarioDefinition extends BuildToolCommandLineScenarioDefinition {
    private final String type;

    public BuckScenarioDefinition(
        String scenarioName,
        @Nullable String title,
        List<String> targets,
        String type,
        List<BuildMutator> buildMutators,
        int warmUpCount,
        int buildCount,
        File outputDir,
        @Nullable File buckHome
    ) {
        super(scenarioName, title, targets, buildMutators, warmUpCount, buildCount, outputDir, buckHome);
        this.type = type;
    }

    @Override
    public String getDisplayName() {
        return getTitle() + " using buck";
    }

    @Override
    public String getProfileName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getBuildToolDisplayName() {
        return "buck";
    }

    public String getType() {
        return type;
    }

    @Override
    public BuildInvoker getInvoker() {
        return BuildInvoker.Buck;
    }

    @Override
    protected void printDetail(PrintStream out) {
        out.println("  Targets: " + getTargets());
        if (getType() != null) {
            out.println("  Type: " + getType());
        }
    }

    @Override
    protected String getExecutableName() {
        if (OperatingSystem.isWindows()) {
            return "buck.exe";
        } else {
            return "buck";
        }
    }

    @Override
    protected String getToolHomeEnvName() {
        return "BUCK_HOME";
    }
}

package org.gradle.profiler.gradle;

import org.gradle.profiler.BuildAction;
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.gradle.GradleBuildInvoker;
import org.gradle.profiler.gradle.GradleScenarioDefinition;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AdhocGradleScenarioDefinition extends GradleScenarioDefinition {
    public AdhocGradleScenarioDefinition(
        GradleBuildConfiguration version,
        GradleBuildInvoker invoker,
        BuildAction buildAction,
        Map<String, String> systemProperties,
        int warmUpCount,
        int buildCount,
        File outputDir,
        List<String> measuredBuildOperations
    ) {
        super(
            "default",
            null,
            invoker,
            version,
            buildAction,
            BuildAction.NO_OP,
            Collections.emptyList(),
            systemProperties,
            Collections.emptyList(),
            warmUpCount,
            buildCount,
            outputDir,
            Collections.emptyList(),
            measuredBuildOperations
        );
    }

    @Override
    public String getDisplayName() {
        return "using " + getBuildConfiguration().getGradleVersion();
    }

    @Override
    public String getProfileName() {
        return getBuildConfiguration().getGradleVersion().getVersion();
    }
}

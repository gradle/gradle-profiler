package org.gradle.profiler;

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

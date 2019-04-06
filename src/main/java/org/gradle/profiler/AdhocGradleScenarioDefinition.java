package org.gradle.profiler;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

public class AdhocGradleScenarioDefinition extends GradleScenarioDefinition {
    public AdhocGradleScenarioDefinition(GradleBuildConfiguration version, GradleBuildInvoker invoker, BuildAction buildAction, Map<String, String> systemProperties, Supplier<BuildMutator> buildMutator, int warmUpCount, int buildCount, File outputDir) {
        super("default", invoker, version, buildAction, BuildAction.NO_OP, Collections.emptyList(), systemProperties, buildMutator, warmUpCount, buildCount, outputDir);
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

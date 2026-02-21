package org.gradle.profiler.ide;

import org.gradle.profiler.BuildAction;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.result.BuildActionResult;

import java.util.List;

/**
 * Build action that triggers a Gradle sync in an IntelliJ-based IDE.
 */
public class IdeSyncAction implements BuildAction {

    public IdeSyncAction() {
    }

    @Override
    public String getShortDisplayName() {
        return "IDE sync";
    }

    @Override
    public String getDisplayName() {
        return "IDE sync";
    }

    @Override
    public boolean isDoesSomething() {
        return true;
    }

    @Override
    public BuildActionResult run(GradleClient gradleClient, List<String> gradleArgs, List<String> jvmArgs) {
        IdeGradleClient ideGradleClient = (IdeGradleClient) gradleClient;
        return ideGradleClient.sync(gradleArgs, jvmArgs);
    }
}

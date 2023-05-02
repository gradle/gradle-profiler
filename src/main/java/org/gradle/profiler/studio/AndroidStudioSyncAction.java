package org.gradle.profiler.studio;

import org.gradle.profiler.BuildAction;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.result.BuildActionResult;
import org.gradle.profiler.studio.StudioGradleClient;

import java.util.List;

/**
 * A mock-up of Android studio sync.
 */
public class AndroidStudioSyncAction implements BuildAction {

    public AndroidStudioSyncAction() {
    }

    @Override
    public String getShortDisplayName() {
        return "AS sync";
    }

    @Override
    public String getDisplayName() {
        return "Android Studio sync";
    }

    @Override
    public boolean isDoesSomething() {
        return true;
    }

    @Override
    public BuildActionResult run(GradleClient gradleClient, List<String> gradleArgs, List<String> jvmArgs) {
        StudioGradleClient studioGradleClient = (StudioGradleClient) gradleClient;
        return studioGradleClient.sync(gradleArgs, jvmArgs);
    }
}

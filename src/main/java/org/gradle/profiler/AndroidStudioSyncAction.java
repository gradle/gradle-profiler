package org.gradle.profiler;

import org.gradle.profiler.studio.StudioGradleClient;

import java.time.Duration;
import java.util.List;

/**
 * A mock-up of Android studio sync.
 */
public class AndroidStudioSyncAction implements BuildAction {

    private final String buildFlavor;
    private final boolean skipSourceGeneration;

    public AndroidStudioSyncAction(String buildFlavor, boolean skipSourceGeneration) {
        this.buildFlavor = buildFlavor;
        this.skipSourceGeneration = skipSourceGeneration;
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
    public Duration run(GradleClient gradleClient, List<String> gradleArgs, List<String> jvmArgs) {
        StudioGradleClient studioGradleClient = (StudioGradleClient) gradleClient;
        return studioGradleClient.sync(gradleArgs, jvmArgs);
    }
}

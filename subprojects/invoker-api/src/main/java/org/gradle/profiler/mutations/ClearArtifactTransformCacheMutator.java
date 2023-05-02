package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;

import java.io.File;

public class ClearArtifactTransformCacheMutator extends AbstractCacheCleanupMutator {

    public ClearArtifactTransformCacheMutator(File gradleUserHome, CleanupSchedule schedule) {
        super(gradleUserHome, schedule, "transforms-");
    }

    @Override
    protected void cleanupCacheDir(File cacheDir) {
        delete(cacheDir);
    }

    public static class Configurator extends AbstractCleanupMutator.Configurator {
        @Override
        protected BuildMutator newInstance(Config scenario, String scenarioName, InvocationSettings settings, String key, CleanupSchedule schedule) {
            return new ClearArtifactTransformCacheMutator(settings.getGradleUserHome(), schedule);
        }
    }
}

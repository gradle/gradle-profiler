package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

import java.io.File;

public class ClearArtifactTransformCacheMutator extends AbstractCacheCleanupMutator {

    public ClearArtifactTransformCacheMutator(File gradleUserHome, Schedule schedule) {
        super(gradleUserHome, schedule, "transforms-");
    }

    @Override
    protected void cleanupCacheDir(File cacheDir) {
        delete(cacheDir);
    }

    public static class Configurator extends AbstractScheduledMutator.Configurator {
        @Override
        protected BuildMutator newInstance(BuildMutatorConfiguratorSpec spec, String key, Schedule schedule) {
            return new ClearArtifactTransformCacheMutator(spec.getGradleUserHome(), schedule);
        }
    }
}

package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

import java.io.File;

public class ClearJarsCacheMutator extends AbstractCacheCleanupMutator {

    public ClearJarsCacheMutator(File gradleUserHome, CleanupSchedule schedule) {
        super(gradleUserHome, schedule, "jars-");
    }

    @Override
    protected void cleanupCacheDir(File cacheDir) {
        delete(cacheDir);
    }

    public static class Configurator extends AbstractCleanupMutator.Configurator {
        @Override
        protected BuildMutator newInstance(BuildMutatorConfiguratorSpec spec, String key, CleanupSchedule schedule) {
            return new ClearJarsCacheMutator(spec.getGradleUserHome(), schedule);
        }
    }
}

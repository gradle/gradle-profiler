package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;

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
        private final File gradleUserHome;

        public Configurator(File gradleUserHome) {
            this.gradleUserHome = gradleUserHome;
        }

        @Override
        protected BuildMutator newInstance(Config scenario, String scenarioName, File projectDir, String key, CleanupSchedule schedule) {
            return new ClearArtifactTransformCacheMutator(gradleUserHome, schedule);
        }
    }
}

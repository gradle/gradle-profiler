package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

public class ClearJarsCacheMutator extends AbstractCacheCleanupMutator {

    public ClearJarsCacheMutator(File gradleUserHome, CleanupSchedule schedule) {
        super(gradleUserHome, schedule, "jars-");
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
            return new ClearJarsCacheMutator(gradleUserHome, schedule);
        }
    }
}

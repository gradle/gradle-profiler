package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

public class ClearBuildCacheMutator extends AbstractCacheCleanupMutator {

    public ClearBuildCacheMutator(File gradleUserHome, CleanupSchedule schedule) {
        super(gradleUserHome, schedule, "build-cache-");
    }

    @Override
    protected void cleanupCacheDir(File cacheDir) {
        Arrays.stream(Objects.requireNonNull(cacheDir.listFiles((file) -> file.getName().length() == 32))).forEach(AbstractCleanupMutator::delete);
    }

    public static class Configurator extends AbstractCleanupMutator.Configurator {
        private final File gradleUserHome;

        public Configurator(File gradleUserHome) {
            this.gradleUserHome = gradleUserHome;
        }

        @Override
        protected BuildMutator newInstance(Config scenario, String scenarioName, File projectDir, String key, CleanupSchedule schedule) {
            return new ClearBuildCacheMutator(gradleUserHome, schedule);
        }
    }
}

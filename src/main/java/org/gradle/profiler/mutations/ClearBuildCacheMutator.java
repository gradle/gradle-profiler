package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

public class ClearBuildCacheMutator extends AbstractCleanupMutator {

    public ClearBuildCacheMutator(File gradleUserHome, CleanupSchedule schedule) {
        super(gradleUserHome, schedule, "build-cache-");
    }

    @Override
    protected void cleanupCacheDir(File cacheDir) {
        Arrays.stream(Objects.requireNonNull(cacheDir.listFiles((file) -> file.getName().length() == 32))).forEach(AbstractCleanupMutator::delete);
    }

    public static class Configurator extends AbstractCleanupMutator.Configurator {

        public Configurator(File gradleUserHome) {
            super(gradleUserHome);
        }

        @Override
        protected BuildMutator newInstance(File gradleUserHome, CleanupSchedule schedule) {
            return new ClearBuildCacheMutator(gradleUserHome, schedule);
        }
    }
}

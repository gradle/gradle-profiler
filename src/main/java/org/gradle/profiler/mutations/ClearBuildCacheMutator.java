package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

public class ClearBuildCacheMutator extends AbstractCacheCleanupMutator {

    public ClearBuildCacheMutator(File gradleUserHome, Schedule schedule) {
        super(gradleUserHome, schedule, "build-cache-");
    }

    @Override
    protected void cleanupCacheDir(File cacheDir) {
        Arrays.stream(Objects.requireNonNull(cacheDir.listFiles(ClearBuildCacheMutator::shouldRemoveFile))).forEach(AbstractScheduledMutator::delete);
    }

    private static boolean shouldRemoveFile(File file) {
        // First generation Build-cache contains hashes of length 32 as names, second generation uses a H2 database
        return file.getName().length() == 32 || file.getName().endsWith(".db");
    }

    public static class Configurator extends AbstractScheduledMutator.Configurator {
        @Override
        protected BuildMutator newInstance(BuildMutatorConfiguratorSpec spec, String key, Schedule schedule) {
            return new ClearBuildCacheMutator(spec.getGradleUserHome(), schedule);
        }
    }
}

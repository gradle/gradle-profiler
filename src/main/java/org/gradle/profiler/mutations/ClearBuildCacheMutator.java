package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;

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
        @Override
        protected BuildMutator newInstance(Config scenario, String scenarioName, InvocationSettings settings, String key, CleanupSchedule schedule) {
            return new ClearBuildCacheMutator(settings.getGradleUserHome(), schedule);
        }
    }
}

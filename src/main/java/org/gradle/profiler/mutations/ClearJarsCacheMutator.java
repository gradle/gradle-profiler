package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;

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
        protected BuildMutator newInstance(Config scenario, String scenarioName, InvocationSettings settings, String key, CleanupSchedule schedule) {
            return new ClearJarsCacheMutator(settings.getGradleUserHome(), schedule);
        }
    }
}

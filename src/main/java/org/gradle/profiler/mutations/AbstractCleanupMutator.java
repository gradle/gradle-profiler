package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ConfigUtil;

import java.io.File;
import java.util.function.Supplier;

public abstract class AbstractCleanupMutator implements BuildMutator {

    private final File gradleUserHome;
    private CleanupSchedule schedule;
    private final String cacheNamePrefix;

    public AbstractCleanupMutator(File gradleUserHome, CleanupSchedule schedule, String cacheNamePrefix) {
        this.gradleUserHome = gradleUserHome;
        this.schedule = schedule;
        this.cacheNamePrefix = cacheNamePrefix;
    }

    @Override
    public void beforeBuild() {
        if (schedule == CleanupSchedule.BUILD) {
            doCleanup();
        }
    }

    @Override
    public void beforeScenario() {
        if (schedule == CleanupSchedule.SCENARIO) {
            doCleanup();
        }
    }

    @Override
    public void beforeCleanup() {
        if (schedule == CleanupSchedule.CLEANUP) {
            doCleanup();
        }
    }

    private void doCleanup() {
        System.out.println("> Cleaning " + cacheNamePrefix + " caches in " + gradleUserHome);
        File cachesDir = new File(gradleUserHome, "caches");
        if (cachesDir.isDirectory()) {
            File[] cacheDirs = cachesDir.listFiles((File file) -> file.getName().startsWith(cacheNamePrefix));
            if (cacheDirs == null) {
                throw new IllegalStateException("Cannot find cache directories in " + gradleUserHome);
            }
            for (File cacheDir : cacheDirs) {
                cleanupCacheDir(cacheDir);
            }
        }
    }

    protected abstract void cleanupCacheDir(File cacheDir);

    public static abstract class Configurator implements BuildMutatorConfigurator {
        private final File gradleUserHome;

        public Configurator(File gradleUserHome) {
            this.gradleUserHome = gradleUserHome;
        }

        @Override
        public Supplier<BuildMutator> configure(Config scenario, String scenarioName, File projectDir, String key) {
            CleanupSchedule schedule = ConfigUtil.enumValue(scenario, key, CleanupSchedule.class, null);
            if (schedule == null) {
                throw new IllegalArgumentException("Schedule for cleanup is not specified");
            }
            return () -> newInstance(gradleUserHome, schedule);
        }

        protected abstract BuildMutator newInstance(File gradleUserHome, CleanupSchedule schedule);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + schedule + ")";
    }

    public enum CleanupSchedule {
        SCENARIO, CLEANUP, BUILD
    }
}

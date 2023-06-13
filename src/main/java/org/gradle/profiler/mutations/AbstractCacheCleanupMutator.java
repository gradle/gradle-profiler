package org.gradle.profiler.mutations;

import java.io.File;

public abstract class AbstractCacheCleanupMutator extends AbstractCleanupMutator {

    private final File gradleUserHome;
    private final String cacheNamePrefix;

    public AbstractCacheCleanupMutator(File gradleUserHome, CleanupSchedule schedule, String cacheNamePrefix) {
        super(schedule);
        this.gradleUserHome = gradleUserHome;
        this.cacheNamePrefix = cacheNamePrefix;
    }

    @Override
    protected void cleanup() {
        System.out.println("> Cleaning " + cacheNamePrefix + " caches in " + gradleUserHome);
        File cachesDir = new File(gradleUserHome, "caches");
        if (cachesDir.isDirectory()) {
            File[] cacheDirs = cachesDir.listFiles((File file) -> file.getName().startsWith(cacheNamePrefix));
            if (cacheDirs == null) {
                throw new IllegalStateException(String.format("Cannot find cache directories with prefix '%s' in %s", cacheNamePrefix, gradleUserHome));
            }
            for (File cacheDir : cacheDirs) {
                if (cacheDir.isDirectory()) {
                    cleanupCacheDir(cacheDir);
                }
            }
        }
    }

    protected abstract void cleanupCacheDir(File cacheDir);
}

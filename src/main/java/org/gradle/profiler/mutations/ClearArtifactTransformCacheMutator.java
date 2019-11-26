package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public class ClearArtifactTransformCacheMutator extends AbstractCacheCleanupMutator {

    public ClearArtifactTransformCacheMutator(File gradleUserHome, CleanupSchedule schedule) {
        super(gradleUserHome, schedule, "transforms-");
    }

    @Override
    protected void cleanupCacheDir(File cacheDir) {
        filesAsStream(
            cacheDir,
            (dir, name) -> name.startsWith("files-")
        ).flatMap(file -> filesAsStream(file, (dir, name) -> !name.endsWith(".lock")))
            .forEach(AbstractCleanupMutator::delete);
    }

    private Stream<File> filesAsStream(File dir, FilenameFilter filter) {
        return Arrays.stream(Objects.requireNonNull(dir.listFiles(filter)));
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

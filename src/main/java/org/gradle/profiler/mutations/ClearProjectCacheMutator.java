package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class ClearProjectCacheMutator extends AbstractCleanupMutator {
    private final File projectDir;

    public ClearProjectCacheMutator(File projectDir, CleanupSchedule schedule) {
        super(schedule);
        this.projectDir = projectDir;
    }

    @Override
    protected void cleanup() {
        deleteGradleCache("project", projectDir);
        File buildSrc = new File(projectDir, "buildSrc");
        if (buildSrc.exists()) {
            deleteGradleCache("buildSrc", buildSrc);
        }
    }

    private void deleteGradleCache(String name, File baseDir) {
        File gradleCache = new File(baseDir, ".gradle");
        System.out.println(String.format("> Cleaning %s .gradle cache: %s", name, gradleCache));
        try {
            FileUtils.deleteDirectory(gradleCache);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class Configurator extends AbstractCleanupMutator.Configurator {
        @Override
        protected BuildMutator newInstance(Config scenario, String scenarioName, InvocationSettings settings, String key, CleanupSchedule schedule) {
            return new ClearProjectCacheMutator(settings.getProjectDir(), schedule);
        }
    }
}

package org.gradle.profiler.mutations;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class ClearProjectCacheMutator extends AbstractScheduledMutator {
    private final File projectDir;

    public ClearProjectCacheMutator(File projectDir, Schedule schedule) {
        super(schedule);
        this.projectDir = projectDir;
    }

    @Override
    protected void executeOnSchedule() {
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

    public static class Configurator extends AbstractScheduledMutator.Configurator {
        @Override
        protected BuildMutator newInstance(BuildMutatorConfiguratorSpec spec, String key, Schedule schedule) {
            return new ClearProjectCacheMutator(spec.getProjectDir(), schedule);
        }
    }
}

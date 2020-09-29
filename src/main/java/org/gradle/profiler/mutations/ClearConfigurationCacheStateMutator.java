package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class ClearConfigurationCacheStateMutator extends AbstractCleanupMutator {
    private final File projectDir;

    public ClearConfigurationCacheStateMutator(File projectDir, CleanupSchedule schedule) {
        super(schedule);
        this.projectDir = projectDir;
    }

    @Override
    protected void cleanup() {
        System.out.println("> Cleaning configuration cache state");
        cleanup(new File(projectDir, ".gradle/configuration-cache"));
        cleanup(new File(projectDir, ".instant-execution-state"));
    }

    private void cleanup(File dir) {
        try {
            FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class Configurator extends AbstractCleanupMutator.Configurator {
        @Override
        protected BuildMutator newInstance(Config scenario, String scenarioName, InvocationSettings settings, String key, CleanupSchedule schedule) {
            return new ClearConfigurationCacheStateMutator(settings.getProjectDir(), schedule);
        }
    }
}

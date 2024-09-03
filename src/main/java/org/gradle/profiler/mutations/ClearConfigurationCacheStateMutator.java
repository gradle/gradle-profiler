package org.gradle.profiler.mutations;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.BuildMutator;

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
    public void validate(BuildInvoker invoker) {
        // Cleaning up the configuration cache should always be safe
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
        protected BuildMutator newInstance(BuildMutatorConfiguratorSpec spec, String key, CleanupSchedule schedule) {
            return new ClearConfigurationCacheStateMutator(spec.getProjectDir(), schedule);
        }
    }
}

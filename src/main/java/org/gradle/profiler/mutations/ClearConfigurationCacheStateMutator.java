package org.gradle.profiler.mutations;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class ClearConfigurationCacheStateMutator extends AbstractScheduledMutator {
    private final File projectDir;

    public ClearConfigurationCacheStateMutator(File projectDir, Schedule schedule) {
        super(schedule);
        this.projectDir = projectDir;
    }

    @Override
    public void validate(BuildInvoker invoker) {
        // Cleaning up the configuration cache should always be safe
    }

    @Override
    protected void executeOnSchedule() {
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

    public static class Configurator extends AbstractScheduledMutator.Configurator {
        @Override
        protected BuildMutator newInstance(BuildMutatorConfiguratorSpec spec, String key, Schedule schedule) {
            return new ClearConfigurationCacheStateMutator(spec.getProjectDir(), schedule);
        }
    }
}

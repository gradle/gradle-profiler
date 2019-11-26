package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class ClearInstantExecutionStateMutator extends AbstractCleanupMutator {
    private final File projectDir;

    public ClearInstantExecutionStateMutator(File projectDir, CleanupSchedule schedule) {
        super(schedule);
        this.projectDir = projectDir;
    }

    @Override
    protected void cleanup() {
        File gradleCache = new File(projectDir, ".instant-execution-state");
        System.out.println(String.format("> Cleaning instant execution state: %s", gradleCache));
        try {
            FileUtils.deleteDirectory(gradleCache);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class Configurator extends AbstractCleanupMutator.Configurator {
        @Override
        protected BuildMutator newInstance(Config scenario, String scenarioName, File projectDir, String key, CleanupSchedule schedule) {
            return new ClearInstantExecutionStateMutator(projectDir, schedule);
        }
    }
}

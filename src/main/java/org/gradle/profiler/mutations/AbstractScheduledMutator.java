package org.gradle.profiler.mutations;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public abstract class AbstractScheduledMutator implements BuildMutator {

    private static final int MAX_DELETE_RETRIES = 7;
    private static final int INITIAL_DELETE_RETRY_DELAY_MS = 100;

    private final Schedule schedule;

    public AbstractScheduledMutator(Schedule schedule) {
        this.schedule = schedule;
    }

    @Override
    public void validate(BuildInvoker invoker) {
        if (schedule != Schedule.SCENARIO && !invoker.allowsMutationBetweenBuilds()) {
            throw new IllegalStateException(this + " is not allowed to be executed between builds with invoker " + invoker);
        }
    }

    @Override
    public void beforeBuild(BuildContext context) {
        if (schedule == Schedule.BUILD) {
            executeOnSchedule();
        }
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        if (schedule == Schedule.SCENARIO) {
            executeOnSchedule();
        }
    }

    @Override
    public void beforeCleanup(BuildContext context) {
        if (schedule == Schedule.CLEANUP) {
            executeOnSchedule();
        }
    }

    abstract protected void executeOnSchedule();

    protected static void deleteFileOrDirectory(File target) {
        if (!target.exists()) {
            return;
        }

        // On Windows, files (especially DLLs) may be locked by processes that haven't fully released them yet.
        // Retry with exponential backoff to handle this.
        int maxAttempts = OperatingSystem.isWindows() ? MAX_DELETE_RETRIES : 1;
        int delayMs = INITIAL_DELETE_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                FileUtils.forceDelete(target);
                return;
            } catch (IOException e) {
                if (attempt == maxAttempts) {
                    throw new UncheckedIOException("Failed to delete '" + target.getAbsolutePath() + "'", e);
                }
                try {
                    Thread.sleep(delayMs);
                    delayMs *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying delete of '" + target.getAbsolutePath() + "'", ie);
                }
            }
        }
    }

    protected static abstract class Configurator implements BuildMutatorConfigurator {
        @Override
        public BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
            Schedule schedule = ConfigUtil.enumValue(spec.getScenario(), key, Schedule.class, null);
            if (schedule == null) {
                throw new IllegalArgumentException("Schedule is not specified");
            }
            return newInstance(spec, key, schedule);
        }

        protected abstract BuildMutator newInstance(BuildMutatorConfiguratorSpec spec, String key, Schedule schedule);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + schedule + ")";
    }

    public enum Schedule {
        SCENARIO, CLEANUP, BUILD
    }

    /**
     * Directory against which to resolve relative paths.
     */
    public enum FileRoot {
        /**
         * --project-dir (default)
         */
        PROJECT,
        /**
         * Gradle User Home, which by default is ./gradle-user-home relative to the working dir, not the project dir.
         */
        GRADLE_USER_HOME
    }
}

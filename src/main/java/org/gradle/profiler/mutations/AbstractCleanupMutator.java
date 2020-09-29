package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public abstract class AbstractCleanupMutator implements BuildMutator {

    private final CleanupSchedule schedule;

    public AbstractCleanupMutator(CleanupSchedule schedule) {
        this.schedule = schedule;
    }

    @Override
    public void validate(BuildInvoker invoker) {
        if (schedule != CleanupSchedule.SCENARIO && !invoker.allowsCleanupBetweenBuilds()) {
            throw new IllegalStateException(this + " is not allowed to be executed between builds with invoker " + invoker);
        }
    }

    @Override
    public void beforeBuild(BuildContext context) {
        if (schedule == CleanupSchedule.BUILD) {
            cleanup();
        }
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        if (schedule == CleanupSchedule.SCENARIO) {
            cleanup();
        }
    }

    @Override
    public void beforeCleanup(BuildContext context) {
        if (schedule == CleanupSchedule.CLEANUP) {
            cleanup();
        }
    }

    abstract protected void cleanup();

    protected static void delete(File f) {
        try {
            if (f.isFile()) {
                Files.delete(f.toPath());
            } else {
                FileUtils.deleteDirectory(f);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not delete " + f, e);
        }
    }

    protected static abstract class Configurator implements BuildMutatorConfigurator {
        @Override
        public BuildMutator configure(Config scenario, String scenarioName, InvocationSettings settings, String key) {
            CleanupSchedule schedule = ConfigUtil.enumValue(scenario, key, CleanupSchedule.class, null);
            if (schedule == null) {
                throw new IllegalArgumentException("Schedule for cleanup is not specified");
            }
            return newInstance(scenario, scenarioName, settings, key, schedule);
        }

        protected abstract BuildMutator newInstance(Config scenario, String scenarioName, InvocationSettings settings, String key, CleanupSchedule schedule);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + schedule + ")";
    }

    public enum CleanupSchedule {
        SCENARIO, CLEANUP, BUILD
    }
}

package org.gradle.profiler.mutations;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public abstract class AbstractScheduledMutator implements BuildMutator {

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
}

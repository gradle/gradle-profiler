package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.apache.tools.ant.util.StringUtils;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.mutations.ClearBuildCacheMutator.CleanupSchedule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class BazelCleanBuildMutator implements BuildMutator {

    private CleanupSchedule schedule;
    private List<String> commands;

    public BazelCleanBuildMutator(CleanupSchedule schedule, List<String> commands) {
        this.schedule = schedule;
        this.commands = commands;
    }

    @Override
    public void beforeBuild() {
        if (schedule == CleanupSchedule.BUILD) {
            clean();
        }
    }

    @Override
    public void beforeScenario() {
        if (schedule == CleanupSchedule.SCENARIO) {
            // always expunge prior to the scenario
            // uses the specific CleanupSchedule to decide if we expunge prior to each BUILD
            clean();
        }
    }

    @Override
    public void beforeCleanup() {
        if (schedule == CleanupSchedule.CLEANUP) {
            clean();
        }
    }

    private void clean() {
        try {
            System.out.println("> Executing cleaning phase with `" + StringUtils.join(commands, " ") + "`");
            ProcessBuilder processBuilder = new ProcessBuilder(commands);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = processBuilder.start();
            int result = process.waitFor();
            System.out.println("Result: " + result);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Configurator implements BuildMutatorConfigurator {

        public Configurator() {
        }

        @Override
        public Supplier<BuildMutator> configure(Config scenario, String scenarioName, File projectDir, String key) {
            List<BazelCleanBuildMutator> mutators = new ArrayList<>();
            List<? extends Config> list = scenario.getConfigList("clean-build-before");
            for (Config config : list) {
                CleanupSchedule schedule = ConfigUtil.enumValue(config, "schedule", CleanupSchedule.class, null);
                if (schedule == null) {
                    throw new IllegalArgumentException("Schedule for cleanup is not specified");
                }
                // default to expunging
                List<String> commands = ConfigUtil.strings(config, "commands", Arrays.asList("bazel", "clean", "--expunge"));
                mutators.add(new BazelCleanBuildMutator(schedule, commands));
            }
            return () -> new CompositeBuildMutator<>(mutators);
        }

        public static class CompositeBuildMutator<T extends BuildMutator> implements BuildMutator {
            final List<T> mutators;

            public CompositeBuildMutator(List<T> mutators) {
                this.mutators = Collections.unmodifiableList(mutators);
            }

            @Override
            public void beforeScenario() {
                for (T mutator : mutators) {
                    mutator.beforeScenario();
                }
            }

            @Override
            public void beforeCleanup() {
                for (T mutator : mutators) {
                    mutator.beforeCleanup();
                }
            }

            @Override
            public void afterCleanup(Throwable error) {
                for (T mutator : mutators) {
                    mutator.afterCleanup(error);
                }
            }

            @Override
            public void beforeBuild() {
                for (T mutator : mutators) {
                    mutator.beforeBuild();
                }
            }

            @Override
            public void afterBuild(Throwable error) {
                for (T mutator : mutators) {
                    mutator.afterBuild(error);
                }
            }

            @Override
            public void afterScenario() {
                for (T mutator : mutators) {
                    mutator.afterScenario();
                }
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + schedule + " command: " + commands + ")";
    }
}

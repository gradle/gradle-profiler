package org.gradle.profiler.mutations;

import static org.gradle.profiler.ScenarioUtil.getBuildConfig;

import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import java.util.ArrayList;
import java.util.List;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CompositeBuildMutator;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ScenarioContext;

public class ExecuteCommandBuildMutator implements BuildMutator {

    private ExecuteCommandSchedule schedule;
    private List<String> commands;
    private CommandInvoker commandInvoker;

    public ExecuteCommandBuildMutator(ExecuteCommandSchedule schedule,
        List<String> commands, CommandInvoker commandInvoker) {
        this.schedule = schedule;
        this.commands = commands;
        this.commandInvoker = commandInvoker;
    }

    @Override
    public void beforeBuild(BuildContext context) {
        if (schedule == ExecuteCommandSchedule.BUILD) {
            execute();
        }
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        if (schedule == ExecuteCommandSchedule.SCENARIO) {
            execute();
        }
    }

    protected void execute() {
        String commandStr = String.join(" ", commands);
        System.out.println(String.format("> Executing command `%s`", commandStr));
        int result = commandInvoker.execute(commands);
        if (result != 0) {
            System.err.println(
                String.format("Unexpected exit code %s for command `%s`", result, commandStr)
            );
        }
    }

    public static class Configurator implements BuildMutatorConfigurator {

        private CommandInvoker commandInvoker;

        public Configurator() {
            this(new ProcessBuilderCommandInvoker());
        }

        @VisibleForTesting
        Configurator(CommandInvoker commandInvoker) {
            this.commandInvoker = commandInvoker;
        }

        private BuildMutator newInstance(Config scenario, String scenarioName,
            InvocationSettings settings, String key,
            CommandInvoker commandInvoker, ExecuteCommandSchedule schedule, List<String> commands) {
            return new ExecuteCommandBuildMutator(schedule, commands, commandInvoker);
        }

        @Override
        public BuildMutator configure(Config rootScenario, String scenarioName,
            InvocationSettings settings, String key) {
            if (enabled(rootScenario, scenarioName, settings, key)) {
                Config scenario = getBuildConfig(rootScenario, settings, null);
                final List<BuildMutator> mutators = new ArrayList<>();
                final List<? extends Config> list = scenario.getConfigList(key);
                for (Config config : list) {
                    final ExecuteCommandSchedule schedule = ConfigUtil
                        .enumValue(config, "schedule", ExecuteCommandSchedule.class, null);
                    if (schedule == null) {
                        throw new IllegalArgumentException(
                            "Schedule for executing commands is not specified");
                    }
                    List<String> commands = ConfigUtil.strings(config, "commands");
                    if (commands.isEmpty()) {
                        throw new IllegalArgumentException(
                            String.format(
                                "No commands specified for 'execute-command-before' in scenario %s",
                                scenarioName)
                        );
                    }
                    mutators.add(
                        newInstance(scenario, scenarioName, settings, key, commandInvoker, schedule,
                            commands));
                }
                return new CompositeBuildMutator(mutators);
            } else {
                return BuildMutator.NOOP;
            }
        }

        private boolean enabled(Config rootScenario, String scenarioName, InvocationSettings settings, String key) {
            Config scenario = getBuildConfig(rootScenario, settings, null);
            return scenario != null && scenario.hasPath(key) && !scenario.getConfigList(key)
                .isEmpty();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + schedule + ")";
    }

    public enum ExecuteCommandSchedule {
        SCENARIO, BUILD
    }

}

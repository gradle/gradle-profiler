package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;

public abstract class AbstractBuildMutatorWithoutOptionsConfigurator implements BuildMutatorConfigurator {

    abstract BuildMutator createBuildMutator(InvocationSettings settings);

    @Override
    public BuildMutator configure(Config scenario, String scenarioName, InvocationSettings settings, String key) {
        boolean enabled = scenario.getBoolean(key);
        return enabled ? createBuildMutator(settings) : BuildMutator.NOOP;
    }
}

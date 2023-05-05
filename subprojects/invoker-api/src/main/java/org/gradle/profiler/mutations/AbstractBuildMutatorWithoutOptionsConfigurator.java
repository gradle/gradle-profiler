package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;

public abstract class AbstractBuildMutatorWithoutOptionsConfigurator implements BuildMutatorConfigurator {

    abstract BuildMutator createBuildMutator(InvocationSettings settings);

    @Override
    public BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
        boolean enabled = spec.getScenario().getBoolean(key);
        return enabled ? createBuildMutator(spec.getInvocationSettings()) : BuildMutator.NOOP;
    }
}

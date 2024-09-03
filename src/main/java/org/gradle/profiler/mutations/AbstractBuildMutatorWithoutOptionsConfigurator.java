package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

public abstract class AbstractBuildMutatorWithoutOptionsConfigurator implements BuildMutatorConfigurator {

    abstract BuildMutator createBuildMutator(BuildMutatorConfiguratorSpec spec);

    @Override
    public BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
        boolean enabled = spec.getScenario().getBoolean(key);
        return enabled ? createBuildMutator(spec) : BuildMutator.NOOP;
    }
}

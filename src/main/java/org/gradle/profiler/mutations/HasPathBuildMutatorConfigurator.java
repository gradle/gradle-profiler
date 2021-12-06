package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import java.util.function.Supplier;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;

class HasPathBuildMutatorConfigurator implements BuildMutatorConfigurator {

    private Supplier<BuildMutator> configurator;

    HasPathBuildMutatorConfigurator(Supplier<BuildMutator> configurator) {
        this.configurator = configurator;
    }

    @Override
    public BuildMutator configure(Config scenario, String scenarioName, InvocationSettings settings, String key) {
        BuildMutator buildMutator;
        if (scenario.hasPath(key)) {
            buildMutator = configurator.get();
        } else {
            buildMutator = BuildMutator.NOOP;
        }
        return buildMutator;
    }
}

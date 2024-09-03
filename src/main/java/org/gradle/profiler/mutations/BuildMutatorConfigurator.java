package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;

public interface BuildMutatorConfigurator {
    BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec);

    interface BuildMutatorConfiguratorSpec {
        Config getScenario();

        String getScenarioName();

        int getWarmupCount();

        int getBuildCount();

        InvocationSettings getInvocationSettings();
    }
}

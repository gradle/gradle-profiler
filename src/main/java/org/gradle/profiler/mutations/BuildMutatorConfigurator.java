package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;

public interface BuildMutatorConfigurator {
    BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec);

    class BuildMutatorConfiguratorSpec {
        private final Config scenario;
        private final String scenarioName;
        private final InvocationSettings invocationSettings;
        private final int warmupCount;
        private final int buildCount;

        public BuildMutatorConfiguratorSpec(Config scenario, String scenarioName, InvocationSettings invocationSettings, int warmupCount, int buildCount) {
            this.scenario = scenario;
            this.scenarioName = scenarioName;
            this.invocationSettings = invocationSettings;
            this.warmupCount = warmupCount;
            this.buildCount = buildCount;
        }

        public Config getScenario() {
            return scenario;
        }

        public String getScenarioName() {
            return scenarioName;
        }

        public int getWarmupCount() {
            return warmupCount;
        }

        public int getBuildCount() {
            return buildCount;
        }

        public InvocationSettings getInvocationSettings() {
            return invocationSettings;
        }

    }

}

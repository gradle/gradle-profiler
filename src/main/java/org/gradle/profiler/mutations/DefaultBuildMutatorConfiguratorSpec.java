package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.mutations.BuildMutatorConfigurator.BuildMutatorConfiguratorSpec;

import java.io.File;

public class DefaultBuildMutatorConfiguratorSpec implements BuildMutatorConfiguratorSpec {
    private final Config scenario;
    private final String scenarioName;
    private final InvocationSettings invocationSettings;
    private final int warmupCount;
    private final int buildCount;

    public DefaultBuildMutatorConfiguratorSpec(Config scenario, String scenarioName, InvocationSettings invocationSettings, int warmupCount, int buildCount) {
        this.scenario = scenario;
        this.scenarioName = scenarioName;
        this.invocationSettings = invocationSettings;
        this.warmupCount = warmupCount;
        this.buildCount = buildCount;
    }

    @Override
    public Config getScenario() {
        return scenario;
    }

    @Override
    public String getScenarioName() {
        return scenarioName;
    }

    @Override
    public int getWarmupCount() {
        return warmupCount;
    }

    @Override
    public int getBuildCount() {
        return buildCount;
    }

    @Override
    public File getScenarioFile() {
        return invocationSettings.getScenarioFile();
    }

    @Override
    public File getProjectDir() {
        return invocationSettings.getProjectDir();
    }

    @Override
    public File getGradleUserHome() {
        return invocationSettings.getGradleUserHome();
    }
}

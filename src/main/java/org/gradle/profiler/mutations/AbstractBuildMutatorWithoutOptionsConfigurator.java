package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;

import java.io.File;

public abstract class AbstractBuildMutatorWithoutOptionsConfigurator implements BuildMutatorConfigurator {

    abstract BuildMutator createBuildMutator(File projectDir);

    @Override
    public BuildMutator configure(Config scenario, String scenarioName, File projectDir, String key) {
        boolean enabled = scenario.getBoolean(key);
        return enabled ? createBuildMutator(projectDir) : BuildMutator.NOOP;
    }
}

package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;

public interface BuildMutatorConfigurator {
	BuildMutator configure(Config scenario, String scenarioName, InvocationSettings settings, String key);
}

package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;

import java.io.File;

public interface BuildMutatorConfigurator {
	BuildMutator configure(Config scenario, String scenarioName, File projectDir, String key);
}

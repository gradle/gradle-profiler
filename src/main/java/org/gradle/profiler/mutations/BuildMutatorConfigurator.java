package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.util.function.Supplier;

public interface BuildMutatorConfigurator {
	Supplier<BuildMutator> configure(Config scenario, String scenarioName, File projectDir, String key);
}

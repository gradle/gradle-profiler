package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ConfigUtil;

import java.io.File;
import java.util.function.Supplier;

public class FileChangeMutatorConfigurator implements BuildMutatorConfigurator {
	private final Class<? extends AbstractFileChangeMutator> mutatorClass;

	public FileChangeMutatorConfigurator(Class<? extends AbstractFileChangeMutator> mutatorClass) {
		this.mutatorClass = mutatorClass;
	}

	@Override
	public Supplier<BuildMutator> configure(Config scenario, String scenarioName, File projectDir, String key) {
		File sourceFileToChange = sourceFile(scenario, scenarioName, projectDir, key);
		if (sourceFileToChange != null) {
			return () -> {
				try {
					return mutatorClass.getConstructor(File.class).newInstance(sourceFileToChange);
				} catch (Exception e) {
					throw new RuntimeException("Could not create instance of mutator " + mutatorClass.getSimpleName(), e);
				}
			};
		} else {
			return null;
		}
	}

	private static File sourceFile(Config config, String scenarioName, File projectDir, String key) {
		File sourceFile = ConfigUtil.file(config, projectDir, key, null);
		if (sourceFile == null) {
			return null;
		}
		if (!sourceFile.isFile()) {
			throw new IllegalArgumentException("Source file " + sourceFile + " specified for scenario " + scenarioName + " does not exist.");
		}
		return sourceFile;
	}
}

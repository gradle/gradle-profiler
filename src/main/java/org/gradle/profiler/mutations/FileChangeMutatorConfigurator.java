package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CompositeBuildMutator;
import org.gradle.profiler.InvocationSettings;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.profiler.mutations.support.ScenarioSupport.sourceFiles;

public class FileChangeMutatorConfigurator implements BuildMutatorConfigurator {
	private final Class<? extends AbstractFileChangeMutator> mutatorClass;

	public FileChangeMutatorConfigurator(Class<? extends AbstractFileChangeMutator> mutatorClass) {
		this.mutatorClass = mutatorClass;
	}

	@Override
	public BuildMutator configure(Config scenario, String scenarioName, InvocationSettings settings, String key) {
		List<BuildMutator> mutatorsForKey = new ArrayList<>();
		for (File sourceFileToChange : sourceFiles(scenario, scenarioName, settings.getProjectDir(), key)) {
			mutatorsForKey.add(getBuildMutatorForFile(sourceFileToChange));
		}

		return new CompositeBuildMutator(mutatorsForKey);
	}

	private BuildMutator getBuildMutatorForFile(File sourceFileToChange) {
		if (sourceFileToChange == null) {
            return null;
        }
        try {
            return mutatorClass.getConstructor(File.class).newInstance(sourceFileToChange);
        } catch (Throwable e) {
            Throwable throwable = (e instanceof InvocationTargetException) ? e.getCause() : e;
            throw new RuntimeException("Could not create instance of mutator " + mutatorClass.getSimpleName(), throwable);
        }
	}
}

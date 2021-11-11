package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CompositeBuildMutator;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.InvocationSettings;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FileChangeMutatorConfigurator implements BuildMutatorConfigurator {
	private final Class<? extends AbstractFileChangeMutator> mutatorClass;

	public FileChangeMutatorConfigurator(Class<? extends AbstractFileChangeMutator> mutatorClass) {
		this.mutatorClass = mutatorClass;
	}

	@Override
	public BuildMutator configure(Config scenario, String scenarioName, InvocationSettings settings, String key) {
		List<BuildMutator> mutatorsForKey = new ArrayList<>();
		for (File sourceFileToChange : sourceFiles(scenario, scenarioName, settings.getProjectDir(), key)) {
			mutatorsForKey.add(getBuildMutatorForFile(scenario, settings, sourceFileToChange));
		}

		return new CompositeBuildMutator(mutatorsForKey);
	}

	protected BuildMutator getBuildMutatorForFile(Config scenario, InvocationSettings settings, File sourceFileToChange) {
		if (sourceFileToChange == null) {
            return null;
        }
        try {
            return newBuildMutator(scenario, settings, sourceFileToChange);
        } catch (Throwable e) {
            Throwable throwable = (e instanceof InvocationTargetException) ? e.getCause() : e;
            throw new RuntimeException("Could not create instance of mutator " + mutatorClass.getSimpleName(), throwable);
        }
	}

    protected BuildMutator newBuildMutator(Config scenario, InvocationSettings settings, File sourceFileToChange)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return mutatorClass.getConstructor(File.class).newInstance(sourceFileToChange);
    }

	protected static List<File> sourceFiles(Config config, String scenarioName, File projectDir, String key) {
		return ConfigUtil.strings(config, key)
				.stream()
				.map(fileName -> openFile(fileName, projectDir, scenarioName))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	private static File openFile(String fileName, File projectDir, String scenarioName) {
		if (fileName == null) {
			return null;
		} else {
			File file = new File(projectDir, fileName);
            System.out.println(file.toPath().toAbsolutePath());
			if (!file.isFile()) {
				throw new IllegalArgumentException("Source file " + file.getName() + " specified for scenario " + scenarioName + " does not exist.");
			}
			return file;
		}
	}
}

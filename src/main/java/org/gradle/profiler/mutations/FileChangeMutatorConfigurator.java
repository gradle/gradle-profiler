package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.BuildMutatorFactory;
import org.gradle.profiler.ConfigUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class FileChangeMutatorConfigurator implements BuildMutatorConfigurator {
    private final Class<? extends AbstractFileChangeMutator> mutatorClass;

    public FileChangeMutatorConfigurator(Class<? extends AbstractFileChangeMutator> mutatorClass) {
        this.mutatorClass = mutatorClass;
    }

    @Override
    public Supplier<BuildMutator> configure(Config scenario, String scenarioName, File projectDir, String key) {
        List<BuildMutator> mutatorsForKey = new ArrayList<>();
        for (File sourceFileToChange : sourceFiles(scenario, scenarioName, projectDir, key)) {
            mutatorsForKey.add(getBuildMutatorForFile(sourceFileToChange));
        }

        return () -> new BuildMutatorFactory.CompositeBuildMutator(mutatorsForKey);
    }

    private BuildMutator getBuildMutatorForFile(File sourceFileToChange) {
        if (sourceFileToChange != null) {
            try {
                try {
                    return mutatorClass.getConstructor(File.class).newInstance(sourceFileToChange);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            } catch (Throwable e) {
                throw new RuntimeException("Could not create instance of mutator " + mutatorClass.getSimpleName(), e);
            }

        } else {
            return null;
        }
    }

    private static List<File> sourceFiles(Config config, String scenarioName, File projectDir, String key) {
        List<File> sourceFiles = ConfigUtil.files(config, projectDir, key, null);
//        if (sourceFiles == null) {
//            return null;
//        }
//        if (!sourceFiles.isFile()) {
//            throw new IllegalArgumentException("Source file " + sourceFiles + " specified for scenario " + scenarioName + " does not exist.");
//        }
        return sourceFiles;
    }
}

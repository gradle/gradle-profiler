package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CompositeBuildMutator;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.mutations.support.ProjectCombinations;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.profiler.mutations.FileChangeMutatorConfigurator.sourceFiles;
import static org.gradle.profiler.mutations.support.ProjectCombinationsSupport.createProjectCombinations;

public class ApplyDependencyChangeMutatorConfigurator implements BuildMutatorConfigurator {

    private static final int DEFAULT_GENERATED_PROJECTS_COUNT = 10;
    private static final int DEFAULT_APPLIED_PROJECTS_COUNT = 5;

    @Override
    public BuildMutator configure(Config scenario, String scenarioName, InvocationSettings settings, String key) {
        List<BuildMutator> mutatorsForKey = new ArrayList<>();
        Config value = scenario.getConfig(key);
        int projectsToGenerate = getProjectsToGenerate(value);
        int appliedProjectDependencies = getAppliedProjectDependencies(value);
        ProjectCombinations combinations = createProjectCombinations(projectsToGenerate, appliedProjectDependencies);

        for (File sourceFileToChange : sourceFiles(value, scenarioName, settings.getProjectDir(), "files")) {
            if (sourceFileToChange != null) {
                mutatorsForKey.add(newBuildMutator(sourceFileToChange, combinations));
            }
        }

        if (!mutatorsForKey.isEmpty()) {
            mutatorsForKey.add(0, new ApplyDependencyChangeSetupMutator(settings.getProjectDir(), combinations));
        }

        return new CompositeBuildMutator(mutatorsForKey);
    }

    private BuildMutator newBuildMutator(File sourceFileToChange, ProjectCombinations projectCombinations) {
        return new ApplyDependencyChangeMutator(sourceFileToChange, projectCombinations);
    }

    private int getProjectsToGenerate(Config config) {
        return config.hasPath("generated-projects-dependencies")
            ? config.getInt("generated-projects-dependencies")
            : DEFAULT_GENERATED_PROJECTS_COUNT;
    }

    private int getAppliedProjectDependencies(Config config) {
        return config.hasPath("applied-projects-dependencies")
            ? config.getInt("applied-projects-dependencies")
            : DEFAULT_APPLIED_PROJECTS_COUNT;
    }
}

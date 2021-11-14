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

public class ApplyProjectDependencyChangeMutatorConfigurator implements BuildMutatorConfigurator {

    public static String PROJECTS_SET_SIZE = "projects-set-size";
    public static String APPLIED_PROJECTS_SET_SIZE = "applied-projects-set-size";

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
            mutatorsForKey.add(0, new ApplyProjectDependencyChangeSetupMutator(settings.getProjectDir(), combinations));
        }

        return new CompositeBuildMutator(mutatorsForKey);
    }

    private BuildMutator newBuildMutator(File sourceFileToChange, ProjectCombinations projectCombinations) {
        return new ApplyProjectDependencyChangeMutator(sourceFileToChange, projectCombinations);
    }

    private int getProjectsToGenerate(Config config) {
        return config.hasPath(PROJECTS_SET_SIZE)
            ? config.getInt(PROJECTS_SET_SIZE)
            : DEFAULT_GENERATED_PROJECTS_COUNT;
    }

    private int getAppliedProjectDependencies(Config config) {
        return config.hasPath(APPLIED_PROJECTS_SET_SIZE)
            ? config.getInt(APPLIED_PROJECTS_SET_SIZE)
            : DEFAULT_APPLIED_PROJECTS_COUNT;
    }
}

package org.gradle.profiler.mutations;

import com.google.common.collect.ImmutableSet;
import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CompositeBuildMutator;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.mutations.ApplyProjectDependencyChangeMutator.ProjectCombinations;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.gradle.profiler.mutations.support.ProjectCombinationsSupport.createProjectCombinations;
import static org.gradle.profiler.mutations.support.ScenarioSupport.sourceFiles;

public class ApplyProjectDependencyChangeMutatorConfigurator implements BuildMutatorConfigurator {

    public static final String APPLIED_PROJECTS_COUNT_KEY = "applied-projects-count";
    private static final String FILES_KEY = "files";
    private static final Set<String> VALID_CONFIG_KEYS = ImmutableSet.of(APPLIED_PROJECTS_COUNT_KEY, FILES_KEY);
    private static final int DEFAULT_APPLIED_PROJECTS_COUNT = 3;

    @Override
    public BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
        Config config = spec.getScenario().getConfig(key);
        validateConfig(key, spec.getScenarioName(), spec.getInvocationSettings().getScenarioFile(), config);
        int appliedProjectCount = getAppliedProjectCount(config);
        InvocationSettings settings = spec.getInvocationSettings();
        List<File> sourceFiles = sourceFiles(config, spec.getScenarioName(), settings.getProjectDir(), FILES_KEY);
        ProjectCombinations combinations = getProjectCombinations(spec, sourceFiles.size(), appliedProjectCount);

        AtomicInteger index = new AtomicInteger();
        List<BuildMutator> mutatorsForKey = sourceFiles.stream()
            .map(sourceFileToChange -> {
                boolean shouldCreateProjects = index.getAndIncrement() == 0;
                return new ApplyProjectDependencyChangeMutator(settings.getProjectDir(), sourceFileToChange, combinations, shouldCreateProjects);
            })
            .collect(Collectors.toList());

        return new CompositeBuildMutator(mutatorsForKey);
    }

    private ProjectCombinations getProjectCombinations(BuildMutatorConfiguratorSpec spec, int numberOfProjects, int appliedProjectDependencies) {
        int numberOfIterations = spec.getWarmupCount() + spec.getBuildCount();
        int numberOfRequiredCombinations = numberOfIterations * numberOfProjects;
        return createProjectCombinations(numberOfRequiredCombinations, appliedProjectDependencies);
    }

    private void validateConfig(String key, String scenarioName, File scenarioFile, Config config) {
        Set<String> invalidKeys = config.entrySet().stream()
            .map(Map.Entry::getKey)
            .filter(entryKey -> !VALID_CONFIG_KEYS.contains(entryKey))
            .collect(Collectors.toSet());
        if (!invalidKeys.isEmpty()) {
            throw new IllegalArgumentException("Unrecognized keys " + invalidKeys + " found for '" + scenarioName + "." + key + "' defined in scenario file " + scenarioFile + ": " + invalidKeys);
        }
    }

    private int getAppliedProjectCount(Config config) {
        return config.hasPath(APPLIED_PROJECTS_COUNT_KEY)
            ? config.getInt(APPLIED_PROJECTS_COUNT_KEY)
            : DEFAULT_APPLIED_PROJECTS_COUNT;
    }
}

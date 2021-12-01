package org.gradle.profiler.mutations.support;

import com.google.common.collect.Sets;
import com.google.common.math.IntMath;
import org.gradle.profiler.mutations.ApplyProjectDependencyChangeMutator.ProjectCombinations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static org.gradle.profiler.mutations.ApplyProjectDependencyChangeMutatorConfigurator.APPLIED_PROJECTS_COUNT_KEY;

public class ProjectCombinationsSupport {

    public static ProjectCombinations createProjectCombinations(int numberOfRequiredCombinations, int appliedProjectsCount) {
        checkArgument(appliedProjectsCount > 0, String.format("Value '%s' should be greater than 0.", APPLIED_PROJECTS_COUNT_KEY));
        int projectsToGenerate = calculateNumberOfProjectsToGenerate(numberOfRequiredCombinations, appliedProjectsCount);
        List<String> projectNames = IntStream.range(0, projectsToGenerate)
            .mapToObj(index -> String.format("generated-dependency-%s", index))
            .collect(Collectors.toList());
        Set<Set<String>> combinations = Sets.combinations(new LinkedHashSet<>(projectNames), appliedProjectsCount);
        return new ProjectCombinations(projectNames, combinations);
    }

    private static int calculateNumberOfProjectsToGenerate(int numberOfRequiredCombinations, int appliedProjectsCount) {
        if (appliedProjectsCount == 1) {
            return numberOfRequiredCombinations;
        }
        int projectsToGenerate = appliedProjectsCount;
        while (IntMath.binomial(projectsToGenerate, appliedProjectsCount) < numberOfRequiredCombinations) {
            // We could be smarter but this is good enough
            // unless we have more billions of iterations
            projectsToGenerate++;
        }
        return projectsToGenerate;
    }
}

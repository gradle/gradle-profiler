package org.gradle.profiler.mutations.support;

import com.google.common.collect.Sets;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static org.gradle.profiler.mutations.ApplyProjectDependencyChangeMutatorConfigurator.APPLIED_PROJECTS_SET_SIZE;
import static org.gradle.profiler.mutations.ApplyProjectDependencyChangeMutatorConfigurator.PROJECTS_SET_SIZE;

public class ProjectCombinationsSupport {

    private ProjectCombinationsSupport() {
    }

    public static ProjectCombinations createProjectCombinations(int projectsToGenerate, int appliedProjectDependencies) {
        checkArgument(projectsToGenerate > 0 && appliedProjectDependencies > 0, String.format("Values '%s' and '%s' should be greater than 0.", PROJECTS_SET_SIZE, APPLIED_PROJECTS_SET_SIZE));
        checkArgument(projectsToGenerate >= appliedProjectDependencies, String.format("Value '%s' should be at least equal to '%s'.", PROJECTS_SET_SIZE, APPLIED_PROJECTS_SET_SIZE));
        String salt = UUID.randomUUID().toString().substring(0, 5);
        List<String> projectNames = IntStream.range(0, projectsToGenerate)
            .mapToObj(it -> String.format("project-%s-%s", salt, it))
            .collect(Collectors.toList());
        Set<Set<String>> combinations = Sets.combinations(new LinkedHashSet<>(projectNames), appliedProjectDependencies);
        return new ProjectCombinations(salt, projectNames, combinations);
    }

}

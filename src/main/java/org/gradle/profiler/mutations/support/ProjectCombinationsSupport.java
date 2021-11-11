package org.gradle.profiler.mutations.support;

import com.google.common.collect.Sets;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;

public class ProjectCombinationsSupport {

    private ProjectCombinationsSupport() {
    }

    public static ProjectCombinations createProjectCombinations(int projectsToGenerate, int appliedProjectDependencies) {
        checkArgument(projectsToGenerate >= appliedProjectDependencies, "Projects to generate should be at least equal to applied project dependencies ");
        String salt = UUID.randomUUID().toString().substring(0, 5);
        List<String> projectNames = IntStream.range(0, projectsToGenerate)
            .mapToObj(it -> String.format("project-%s-%s", salt, it))
            .collect(Collectors.toList());
        Set<Set<String>> combinations = Sets.combinations(new LinkedHashSet<>(projectNames), appliedProjectDependencies);
        return new ProjectCombinations(salt, projectNames, combinations);
    }

}

package org.gradle.profiler.mutations.support;

import com.google.common.collect.Sets;
import com.google.common.math.IntMath;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ProjectCombinationsSupport {

    /**
     * From 34 choose 17 = 2333606220 that is more than an integer
     * From 33 choose 16 = 1166803110 that is less than an integer, so we choose max n as 33
     */
    private static final int MAX_N = 33;

    private ProjectCombinationsSupport() {
    }

    /**
     * We use combinations of generated projects. We don't try all combinations, but just combinations that satisfies:
     * "from n choose n / 2".
     *
     * First we find n for given iterations. We do that by finding lowest n that satisfies condition:
     * "from n choose n / 2" >= iterations.
     *
     * After that we create all such combinations "from n choose n / 2".
     *
     * Why not all combinations? Because it takes some extra steps and with n / 2 we can easily get high number of combinations
     * with small number of projects. For example with "from 33 choose 16" we get 1166803110 combinations.
     */
    public static ProjectCombinations createProjectCombinations(int mutatorIndex, int numberOfIterations) {
        int numberOfProjects = findNumberOfProjects(numberOfIterations);
        List<String> projectNames = IntStream.range(0, numberOfProjects)
            .mapToObj(it -> String.format("project-%d-%d", mutatorIndex, it))
            .collect(Collectors.toList());
        int numberOfProjectsInOneCombination = getNumberOfProjectsInOneCombination(projectNames.size());
        Set<Set<String>> combinations = Sets.combinations(new LinkedHashSet<>(projectNames), numberOfProjectsInOneCombination);
        return new ProjectCombinations(projectNames, combinations);
    }

    private static int findNumberOfProjects(int numberOfIterations) {
        for (int n = 1; n <= MAX_N; n++) {
            int k = getNumberOfProjectsInOneCombination(n);
            int combinations = IntMath.binomial(n, k);
            if (combinations >= numberOfIterations) {
                return n;
            }
        }
        throw new IllegalStateException("Too many warm up count and build count set");
    }

    private static int getNumberOfProjectsInOneCombination(int n) {
        return n <= 1 ? 1 : n / 2;
    }

}

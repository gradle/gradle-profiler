package org.gradle.profiler.mutations


import spock.lang.Specification

import static org.gradle.profiler.mutations.ApplyProjectDependencyChangeMutator.ProjectCombinations.createProjectCombinations

class ProjectCombinationsTest extends Specification {

    def "creates combinations for given number of required combinations"() {
        when:
        def combinations = createProjectCombinations(4, 2)

        then:
        combinations.getProjectNames() == ["generated-dependency-0", "generated-dependency-1", "generated-dependency-2", "generated-dependency-3"] as List<String>
        def expectedCombinations = [
            ["generated-dependency-0", "generated-dependency-1"] as Set<String>,
            ["generated-dependency-0", "generated-dependency-2"] as Set<String>,
            ["generated-dependency-0", "generated-dependency-3"] as Set<String>,
            ["generated-dependency-1", "generated-dependency-2"] as Set<String>,
            ["generated-dependency-1", "generated-dependency-3"] as Set<String>,
            ["generated-dependency-2", "generated-dependency-3"] as Set<String>,
        ] as Set<Set<String>>
        def givenCombinations = [
            combinations.nextCombination,
            combinations.nextCombination,
            combinations.nextCombination,
            combinations.nextCombination,
            combinations.nextCombination,
            combinations.nextCombination
        ] as Set<Set<String>>
        expectedCombinations == givenCombinations
    }

}

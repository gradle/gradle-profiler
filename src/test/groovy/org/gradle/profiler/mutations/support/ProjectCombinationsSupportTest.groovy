package org.gradle.profiler.mutations.support

import spock.lang.Specification

class ProjectCombinationsSupportTest extends Specification {

    def "creates combinations for given number of required combinations"() {
        when:
        def combinations = ProjectCombinationsSupport.createProjectCombinations(4, 2)

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

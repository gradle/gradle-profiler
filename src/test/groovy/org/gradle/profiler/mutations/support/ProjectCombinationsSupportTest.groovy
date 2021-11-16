package org.gradle.profiler.mutations.support

import spock.lang.Specification

import static org.gradle.profiler.mutations.support.ProjectCombinationsSupport.PROJECT_HASH

class ProjectCombinationsSupportTest extends Specification {

    def "creates combinations for given number of required combinations"() {
        when:
        def combinations = ProjectCombinationsSupport.createProjectCombinations(4, 2)

        then:
        combinations.getProjectNames() == ["project-$PROJECT_HASH-0", "project-$PROJECT_HASH-1", "project-$PROJECT_HASH-2", "project-$PROJECT_HASH-3"] as List<String>
        def expectedCombinations = [
            ["project-$PROJECT_HASH-0", "project-$PROJECT_HASH-1"] as Set<String>,
            ["project-$PROJECT_HASH-0", "project-$PROJECT_HASH-2"] as Set<String>,
            ["project-$PROJECT_HASH-0", "project-$PROJECT_HASH-3"] as Set<String>,
            ["project-$PROJECT_HASH-1", "project-$PROJECT_HASH-2"] as Set<String>,
            ["project-$PROJECT_HASH-1", "project-$PROJECT_HASH-3"] as Set<String>,
            ["project-$PROJECT_HASH-2", "project-$PROJECT_HASH-3"] as Set<String>,
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

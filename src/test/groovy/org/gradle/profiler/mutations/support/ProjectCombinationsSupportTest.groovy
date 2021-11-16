package org.gradle.profiler.mutations.support

import spock.lang.Specification

import static org.gradle.profiler.mutations.support.ProjectCombinationsSupport.PROJECT_HASH

class ProjectCombinationsSupportTest extends Specification {

    def "creates combinations for given number of projects"() {
        when:
        def combinations = ProjectCombinationsSupport.createProjectCombinations(3, 2)

        then:
        combinations.nextCombination == ["project-$PROJECT_HASH-0", "project-$PROJECT_HASH-1"] as Set<String>
        combinations.nextCombination == ["project-$PROJECT_HASH-0", "project-$PROJECT_HASH-2"] as Set<String>
        combinations.nextCombination == ["project-$PROJECT_HASH-1", "project-$PROJECT_HASH-2"] as Set<String>
        // Combinations repeats circular
        combinations.nextCombination == ["project-$PROJECT_HASH-0", "project-$PROJECT_HASH-1"] as Set<String>
        combinations.nextCombination == ["project-$PROJECT_HASH-0", "project-$PROJECT_HASH-2"] as Set<String>
        combinations.nextCombination == ["project-$PROJECT_HASH-1", "project-$PROJECT_HASH-2"] as Set<String>
    }

}

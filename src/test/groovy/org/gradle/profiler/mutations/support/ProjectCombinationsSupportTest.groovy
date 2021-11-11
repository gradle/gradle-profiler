package org.gradle.profiler.mutations.support

import spock.lang.Specification

class ProjectCombinationsSupportTest extends Specification {

    def "creates combinations for given number of projects"() {
        when:
        def combinations = ProjectCombinationsSupport.createProjectCombinations(3, 2)

        then:
        def salt = combinations.getSalt()
        combinations.nextCombination == ["project-$salt-0", "project-$salt-1"] as Set<String>
        combinations.nextCombination == ["project-$salt-0", "project-$salt-2"] as Set<String>
        combinations.nextCombination == ["project-$salt-1", "project-$salt-2"] as Set<String>
        // Combinations repeats circular
        combinations.nextCombination == ["project-$salt-0", "project-$salt-1"] as Set<String>
        combinations.nextCombination == ["project-$salt-0", "project-$salt-2"] as Set<String>
        combinations.nextCombination == ["project-$salt-1", "project-$salt-2"] as Set<String>
    }

}

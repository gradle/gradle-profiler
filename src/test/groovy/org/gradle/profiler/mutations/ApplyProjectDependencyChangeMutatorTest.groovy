package org.gradle.profiler.mutations

import org.gradle.profiler.mutations.support.ProjectCombinations

import static org.gradle.profiler.mutations.support.ProjectCombinationsSupport.*

class ApplyProjectDependencyChangeMutatorTest extends AbstractMutatorTest {

    ProjectCombinations combinations
    String salt

    def setup() {
        combinations = createProjectCombinations(2, 2)
        salt = combinations.getSalt()
    }

    def "adds projects dependency at the end of the build script"() {
        def sourceFile = tmpDir.newFile("build.gradle")
        sourceFile.text = "plugins { id 'java' }"
        def mutator = new ApplyProjectDependencyChangeMutator(sourceFile, combinations)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == """plugins { id 'java' }
dependencies {
    project(":project-$salt-0")
    project(":project-$salt-1")
}"""
    }

    def "reverts projects dependency changes"() {
        def sourceFile = tmpDir.newFile("build.gradle")
        sourceFile.text = "plugins { id 'java' }"
        def mutator = new ApplyProjectDependencyChangeMutator(sourceFile, combinations)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == """plugins { id 'java' }"""
    }

}

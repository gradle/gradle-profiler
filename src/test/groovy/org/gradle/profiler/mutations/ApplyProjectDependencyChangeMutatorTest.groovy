package org.gradle.profiler.mutations

import org.gradle.profiler.mutations.support.ProjectCombinations

import static org.gradle.profiler.mutations.support.ProjectCombinationsSupport.*

class ApplyProjectDependencyChangeMutatorTest extends AbstractMutatorTest {

    ProjectCombinations combinations

    def setup() {
        combinations = createProjectCombinations(2, 2)
    }

    def "adds projects dependency at the end of the build script"() {
        def sourceFile = tmpDir.newFile("build.gradle")
        sourceFile.text = "plugins { id 'java' }"
        def mutator = new ApplyProjectDependencyChangeMutator(sourceFile, combinations)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == """plugins { id 'java' }
dependencies {
    project(":project-$PROJECT_HASH-0")
    project(":project-$PROJECT_HASH-1")
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

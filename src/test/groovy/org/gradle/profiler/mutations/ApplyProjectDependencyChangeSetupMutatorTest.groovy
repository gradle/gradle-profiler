package org.gradle.profiler.mutations

import org.gradle.profiler.mutations.support.ProjectCombinations

import static org.gradle.profiler.mutations.support.ProjectCombinationsSupport.createProjectCombinations

class ApplyProjectDependencyChangeSetupMutatorTest extends AbstractMutatorTest {

    ProjectCombinations combinations
    String salt

    def setup() {
        combinations = createProjectCombinations(2, 1)
        salt = combinations.getSalt()
    }

    def "throws exception is settings file does not exist"() {
        def combinations = createProjectCombinations(2, 1)

        when:
        new ApplyProjectDependencyChangeSetupMutator(tmpDir.root, combinations)

        then:
        def e = thrown(IllegalStateException)
        e.message == "No settings.gradle(.kts) file found in ${tmpDir.root}"
    }

    def "generates projects to gradle-profiler-generated-projects folder"() {
        tmpDir.newFile("settings.gradle")
        def mutator = new ApplyProjectDependencyChangeSetupMutator(tmpDir.root, combinations)

        when:
        mutator.beforeScenario(scenarioContext)

        then:
        File project1BuildScript = file(tmpDir.root, "gradle-profiler-generated-projects", "project-$salt-0", "build.gradle")
        project1BuildScript.exists()
        project1BuildScript.text == "plugins { id 'java' }"
        File project2BuildScript = file(tmpDir.root, "gradle-profiler-generated-projects", "project-$salt-1", "build.gradle")
        project2BuildScript.exists()
        project2BuildScript.text == "plugins { id 'java' }"
    }

    def "removes projects after scenario"() {
        tmpDir.newFile("settings.gradle")
        def mutator = new ApplyProjectDependencyChangeSetupMutator(tmpDir.root, combinations)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.afterScenario(scenarioContext)

        then:
        !file(tmpDir.root, "gradle-profiler-generated-projects").exists()
    }

    def "adds generated projects and it's location at the end of the settings scripts"() {
        def settingsFile = tmpDir.newFile("settings.gradle")
        settingsFile.text = "rootProject.name = 'test-project'"
        def mutator = new ApplyProjectDependencyChangeSetupMutator(tmpDir.root, combinations)

        when:
        mutator.beforeScenario(scenarioContext)

        then:
        settingsFile.text == """rootProject.name = 'test-project'
include("project-$salt-0")
project(":project-$salt-0").projectDir = file("gradle-profiler-generated-projects/project-$salt-0")
include("project-$salt-1")
project(":project-$salt-1").projectDir = file("gradle-profiler-generated-projects/project-$salt-1")"""
    }

    def "reverts settings file changes"() {
        def sourceFile = tmpDir.newFile("settings.gradle")
        sourceFile.text = "rootProject.name = 'test-project'"
        def mutator = new ApplyProjectDependencyChangeSetupMutator(tmpDir.root, combinations)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "rootProject.name = 'test-project'"
    }

}

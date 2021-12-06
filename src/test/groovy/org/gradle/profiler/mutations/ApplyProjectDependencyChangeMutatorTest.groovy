package org.gradle.profiler.mutations

import static org.gradle.profiler.mutations.ApplyProjectDependencyChangeMutator.ProjectCombinations
import static org.gradle.profiler.mutations.ApplyProjectDependencyChangeMutator.ProjectCombinations.createProjectCombinations

class ApplyProjectDependencyChangeMutatorTest extends AbstractMutatorTest {

    ProjectCombinations combinations
    File settingsFile
    File buildFile

    def setup() {
        combinations = createProjectCombinations(2, 2)
        settingsFile = tmpDir.newFile("settings.gradle")
        buildFile = tmpDir.newFile("build.gradle")
    }

    def "throws exception is settings file does not exist"() {
        settingsFile.delete()
        def combinations = createProjectCombinations(2, 1)

        when:
        new ApplyProjectDependencyChangeMutator(tmpDir.root, buildFile, combinations, true)

        then:
        def e = thrown(IllegalStateException)
        e.message == "No settings.gradle(.kts) file found in ${tmpDir.root}"
    }

    def "generates projects to gradle-profiler-generated-projects folder"() {
        def mutator = new ApplyProjectDependencyChangeMutator(tmpDir.root, buildFile, combinations, true)

        when:
        mutator.beforeScenario(scenarioContext)

        then:
        File project1BuildScript = new File(tmpDir.root, "gradle-profiler-generated-projects/generated-dependency-0/build.gradle")
        project1BuildScript.exists()
        project1BuildScript.text == "plugins { id 'java' }"
        File project2BuildScript = new File(tmpDir.root, "gradle-profiler-generated-projects/generated-dependency-1/build.gradle")
        project2BuildScript.exists()
        project2BuildScript.text == "plugins { id 'java' }"
    }

    def "doesn't generate projects to gradle-profiler-generated-projects folder if mutator with shouldCreateProjects set to false"() {
        def mutator = new ApplyProjectDependencyChangeMutator(tmpDir.root, buildFile, combinations, false)

        when:
        mutator.beforeScenario(scenarioContext)

        then:
        !new File(tmpDir.root, "gradle-profiler-generated-projects").exists()
    }

    def "removes projects after scenario"() {
        def mutator = new ApplyProjectDependencyChangeMutator(tmpDir.root, buildFile, combinations, true)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.afterScenario(scenarioContext)

        then:
        !new File(tmpDir.root, "gradle-profiler-generated-projects").exists()
    }

    def "adds generated projects and it's location at the end of the settings scripts"() {
        settingsFile.text = "rootProject.name = 'test-project'"
        def mutator = new ApplyProjectDependencyChangeMutator(tmpDir.root, buildFile, combinations, true)

        when:
        mutator.beforeScenario(scenarioContext)

        then:
        println settingsFile.text
        settingsFile.text == """rootProject.name = 'test-project'
include("generated-dependency-0")
project(":generated-dependency-0").projectDir = file("gradle-profiler-generated-projects/generated-dependency-0")
include("generated-dependency-1")
project(":generated-dependency-1").projectDir = file("gradle-profiler-generated-projects/generated-dependency-1")
include("generated-dependency-2")
project(":generated-dependency-2").projectDir = file("gradle-profiler-generated-projects/generated-dependency-2")"""
    }

    def "reverts settings file changes"() {
        settingsFile.text = "rootProject.name = 'test-project'"
        def mutator = new ApplyProjectDependencyChangeMutator(tmpDir.root, buildFile, combinations, true)

        when:
        mutator.beforeScenario(scenarioContext)
        settingsFile.text = "some modification"
        mutator.afterScenario(scenarioContext)

        then:
        settingsFile.text == "rootProject.name = 'test-project'"
    }

    def "doesn't revert settings file changes if is a mutator with shouldCreateProjects set to false"() {
        settingsFile.text = "rootProject.name = 'test-project'"
        def mutator = new ApplyProjectDependencyChangeMutator(tmpDir.root, buildFile, combinations, false)

        when:
        mutator.beforeScenario(scenarioContext)
        settingsFile.text = "some modification"
        mutator.afterScenario(scenarioContext)

        then:
        settingsFile.text == "some modification"
    }

    def "adds projects dependency at the end of the build script"() {
        buildFile.text = "plugins { id 'java' }"
        def mutator = new ApplyProjectDependencyChangeMutator(tmpDir.root, buildFile, combinations, true)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)

        then:
        buildFile.text == """plugins { id 'java' }
dependencies {
    project(":generated-dependency-0")
    project(":generated-dependency-1")
}"""
    }

    def "reverts projects dependency changes"() {
        buildFile.text = "plugins { id 'java' }"
        def mutator = new ApplyProjectDependencyChangeMutator(tmpDir.root, buildFile, combinations, true)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)
        mutator.afterScenario(scenarioContext)

        then:
        buildFile.text == """plugins { id 'java' }"""
    }

}

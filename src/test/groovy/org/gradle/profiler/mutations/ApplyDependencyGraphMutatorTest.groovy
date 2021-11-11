package org.gradle.profiler.mutations

class ApplyDependencyGraphMutatorTest extends AbstractMutatorTest {

    def "throws exception is settings file does not exist"() {
        def sourceFile = tmpDir.newFile("build.gradle")

        when:
        new ApplyDependencyChangeMutator(sourceFile, tmpDir.root, 0)

        then:
        def e = thrown(IllegalStateException)
        e.message == "No settings.gradle(.kts) file found in ${sourceFile.parentFile}"
    }

    def "generates projects to gradle-profiler-generated-projects folder"() {
        def sourceFile = tmpDir.newFile("settings.gradle")
        def mutator = new ApplyDependencyChangeMutator(sourceFile, tmpDir.root, 0)
        scenarioDefinition.buildCount >> 2
        scenarioDefinition.warmUpCount >> 0

        when:
        mutator.beforeScenario(scenarioContext)

        then:
        File project1BuildScript = new File(tmpDir.root, "gradle-profiler-generated-projects/project-0-0/build.gradle".replace("/", File.separator))
        project1BuildScript.exists()
        project1BuildScript.text == "plugins { id 'java' }"
        File project2BuildScript = new File(tmpDir.root, "gradle-profiler-generated-projects/project-0-0/build.gradle".replace("/", File.separator))
        project2BuildScript.exists()
        project2BuildScript.text == "plugins { id 'java' }"
    }

    def "removes projects after scenario"() {
        def sourceFile = tmpDir.newFile("settings.gradle")
        def mutator = new ApplyDependencyChangeMutator(sourceFile, tmpDir.root, 0)
        scenarioDefinition.buildCount >> 2
        scenarioDefinition.warmUpCount >> 0

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.afterScenario(scenarioContext)

        then:
        !new File(tmpDir.root, "gradle-profiler-generated-projects").exists()
    }

    def "adds generated projects and it's location at the end of the settings scripts"() {
        def sourceFile = tmpDir.newFile("settings.gradle")
        sourceFile.text = "rootProject.name = 'test-project'"
        def mutator = new ApplyDependencyChangeMutator(sourceFile, tmpDir.root, 0)
        scenarioDefinition.buildCount >> 2
        scenarioDefinition.warmUpCount >> 0

        when:
        mutator.beforeScenario(scenarioContext)

        then:
        sourceFile.text == """rootProject.name = 'test-project'
include("project-0-0")
project(":project-0-0").projectDir = file("gradle-profiler-generated-projects/project-0-0")
include("project-0-1")
project(":project-0-1").projectDir = file("gradle-profiler-generated-projects/project-0-1")"""
    }

    def "reverts settings file changes"() {
        def sourceFile = tmpDir.newFile("settings.gradle")
        sourceFile.text = "rootProject.name = 'test-project'"
        def mutator = new ApplyDependencyChangeMutator(sourceFile, tmpDir.root, 0)
        scenarioDefinition.buildCount >> 1
        scenarioDefinition.warmUpCount >> 0

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "rootProject.name = 'test-project'"
    }

    def "adds projects dependency at the end of the build script"() {
        tmpDir.newFile("settings.gradle")
        def sourceFile = tmpDir.newFile("build.gradle")
        sourceFile.text = "plugins { id 'java' }"
        def mutator = new ApplyDependencyChangeMutator(sourceFile, tmpDir.root, 0)
        scenarioDefinition.buildCount >> 1
        scenarioDefinition.warmUpCount >> 0

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == """plugins { id 'java' }
dependencies {
    project(":project-0-0")
}"""
    }

    def "reverts projects dependency changes"() {
        tmpDir.newFile("settings.gradle").createNewFile()
        def sourceFile = tmpDir.newFile("build.gradle")
        sourceFile.text = "plugins { id 'java' }"
        def mutator = new ApplyDependencyChangeMutator(sourceFile, tmpDir.root, 0)
        scenarioDefinition.buildCount >> 1
        scenarioDefinition.warmUpCount >> 0

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == """plugins { id 'java' }"""
    }

}

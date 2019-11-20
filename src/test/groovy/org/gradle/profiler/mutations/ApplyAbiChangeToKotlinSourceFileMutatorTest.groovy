package org.gradle.profiler.mutations

class ApplyAbiChangeToKotlinSourceFileMutatorTest extends AbstractMutatorTest {

    def "adds and replaces public method at end of source file"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = "class Thing { fun existingMethod() { }}"
        def mutator = new ApplyAbiChangeToKotlinSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}fun _mUNIQUE_ID() {}"
        1 * buildContext.uniqueBuildId >> "UNIQUE_ID"
        0 * _
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = "class Thing { fun existingMethod() { }}"
        def mutator = new ApplyAbiChangeToKotlinSourceFileMutator(sourceFile)

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}"

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}"
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = "class Thing { fun existingMethod() { }}"
        def mutator = new ApplyAbiChangeToKotlinSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}"

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}"
    }
}

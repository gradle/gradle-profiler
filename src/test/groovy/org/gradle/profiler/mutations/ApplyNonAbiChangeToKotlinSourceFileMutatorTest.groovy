package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApplyNonAbiChangeToKotlinSourceFileMutatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "adds and replaces public method at end of source file"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = "class Thing { fun existingMethod() { }}"
        def mutator = new ApplyNonAbiChangeToKotlinSourceFileMutator(sourceFile)
        mutator.timestamp = 1234

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}private fun _m_1234_1() {}"

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}private fun _m_1234_2() {}"

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}private fun _m_1234_3() {}"
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = "class Thing { fun existingMethod() { }}"
        def mutator = new ApplyNonAbiChangeToKotlinSourceFileMutator(sourceFile)

        when:
        mutator.afterScenario()

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}"

        when:
        mutator.afterScenario()

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}"
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = "class Thing { fun existingMethod() { }}"
        def mutator = new ApplyNonAbiChangeToKotlinSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild()
        mutator.afterScenario()

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}"

        when:
        mutator.afterScenario()

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}"
    }
}

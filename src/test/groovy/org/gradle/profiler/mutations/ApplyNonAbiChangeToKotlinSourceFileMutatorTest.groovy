package org.gradle.profiler.mutations

import org.gradle.profiler.Phase

import java.util.function.Function

class ApplyNonAbiChangeToKotlinSourceFileMutatorTest extends AbstractMutatorTest {

    static Function<String, String> FUNCTION_TEXT = { qualifier ->
        "class Thing { fun existingMethod() { }}\n" +
            "private fun _m_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7() {" +
            "requireNotNull(\"_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_$qualifier\")" +
            "}"
    }

    def "adds public function at end of source file replacing its body"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = "class Thing { fun existingMethod() { }}"
        def mutator = new ApplyNonAbiChangeToKotlinSourceFileMutator(sourceFile)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == FUNCTION_TEXT.apply("MEASURE_7")

        when:
        mutator.beforeBuild(buildContext.withBuild(Phase.MEASURE, 8))

        then:
        sourceFile.text == FUNCTION_TEXT.apply("MEASURE_8")
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = "class Thing { fun existingMethod() { }}"
        def mutator = new ApplyNonAbiChangeToKotlinSourceFileMutator(sourceFile)

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
        def mutator = new ApplyNonAbiChangeToKotlinSourceFileMutator(sourceFile)

        when:
        mutator.beforeScenario(scenarioContext)
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

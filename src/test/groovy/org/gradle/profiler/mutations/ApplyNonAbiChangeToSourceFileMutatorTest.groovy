package org.gradle.profiler.mutations

import static com.github.javaparser.JavaParser.parse

class ApplyNonAbiChangeToSourceFileMutatorTest extends AbstractMutatorTest {

    def "adds and replaces public method at end of Kotlin source file"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = "class Thing { fun existingMethod() { }}"
        def mutator = new ApplyNonAbiChangeToSourceFileMutator(sourceFile)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == ApplyNonAbiChangeToKotlinSourceFileMutatorTest.FUNCTION_TEXT.apply("MEASURE_7")
    }

    def "adds and replaces public method at end of Java source file"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { public void existingMethod() { }}"
        def mutator = new ApplyNonAbiChangeToSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        parse(sourceFile) == parse('class Thing { public void existingMethod() { System.out.println("_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7");}}')
    }
}

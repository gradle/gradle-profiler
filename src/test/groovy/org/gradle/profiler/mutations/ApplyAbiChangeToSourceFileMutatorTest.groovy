package org.gradle.profiler.mutations

import static com.github.javaparser.JavaParser.parse

class ApplyAbiChangeToSourceFileMutatorTest extends AbstractMutatorTest {

    def "adds and replaces public method at end of Kotlin source file"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = "class Thing { fun existingMethod() { }}"
        def mutator = new ApplyAbiChangeToSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}fun _m_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7() {}"
    }

    def "adds and replaces public method at end of Java source file"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { public void existingMethod() { }}"
        def mutator = new ApplyAbiChangeToSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        parse(sourceFile) == parse("class Thing { public void existingMethod() { _m_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7();}public static void _m_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7() { }}")
    }
}

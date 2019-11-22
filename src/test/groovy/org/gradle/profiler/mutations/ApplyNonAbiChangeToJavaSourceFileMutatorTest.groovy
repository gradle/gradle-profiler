package org.gradle.profiler.mutations

import static com.github.javaparser.JavaParser.parse

class ApplyNonAbiChangeToJavaSourceFileMutatorTest extends AbstractMutatorTest {

    def "changes the first method in the source file"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { public void existingMethod() { }}"
        def mutator = new ApplyNonAbiChangeToJavaSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        parse(sourceFile) == parse('class Thing { public void existingMethod() { System.out.println("_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7");}}')
    }

    def "does not work with Java files that do not contain a method"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { }"
        def mutator = new ApplyNonAbiChangeToJavaSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        IllegalArgumentException t = thrown()
        t.message == "No methods to change in " + sourceFile
    }
}

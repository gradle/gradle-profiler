package org.gradle.profiler.mutations


import static com.github.javaparser.JavaParser.parse

class ApplyNonAbiChangeToSourceFileMutatorTest extends AbstractMutatorTest {

    def "adds and replaces public method at end of Kotlin source file"() {
        def sourceFile = tmpDir.newFile("Thing.kt")
        sourceFile.text = "class Thing { fun existingMethod() { }}"
        def mutator = new ApplyNonAbiChangeToSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == "class Thing { fun existingMethod() { }}private fun _mUNIQUE_ID() {}"
        1 * buildContext.uniqueBuildId >> "UNIQUE_ID"
        0 * _
    }

    def "adds and replaces public method at end of Java source file"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { public void existingMethod() { }}"
        def mutator = new ApplyNonAbiChangeToSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        parse(sourceFile) == parse('class Thing { public void existingMethod() { System.out.println("UNIQUE_ID");}}')
        1 * buildContext.uniqueBuildId >> "UNIQUE_ID"
        0 * _
    }
}

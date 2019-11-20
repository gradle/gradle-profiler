package org.gradle.profiler.mutations


import static com.github.javaparser.JavaParser.parse

class ApplyAbiChangeToJavaSourceFileMutatorTest extends AbstractMutatorTest {

    def "adds and replaces public method at end of source file"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { public void existingMethod() { }}"
        def mutator = new ApplyAbiChangeToJavaSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        parse(sourceFile) == parse("class Thing { public void existingMethod() { _mUNIQUE_ID();}public static void _mUNIQUE_ID() { }}")
        1 * buildContext.uniqueBuildId >> "UNIQUE_ID"
        0 * _
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { public void existingMethod() { }}"
        def mutator = new ApplyAbiChangeToJavaSourceFileMutator(sourceFile)

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "class Thing { public void existingMethod() { }}"

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "class Thing { public void existingMethod() { }}"
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { public void existingMethod() { }}"
        def mutator = new ApplyAbiChangeToJavaSourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "class Thing { public void existingMethod() { }}"

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "class Thing { public void existingMethod() { }}"
    }
}

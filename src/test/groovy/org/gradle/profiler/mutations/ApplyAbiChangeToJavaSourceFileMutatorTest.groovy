package org.gradle.profiler.mutations


import static com.github.javaparser.JavaParser.parse

class ApplyAbiChangeToJavaSourceFileMutatorTest extends AbstractMutatorTest {

    def "adds and replaces public method at end of source file"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { public void existingMethod() { }}"
        def mutator = new ApplyAbiChangeToJavaSourceFileMutator(sourceFile)
        mutator.timestamp = 1234

        when:
        mutator.beforeBuild(buildContext)

        then:
        parse(sourceFile) == parse("class Thing { public void existingMethod() { _m_1234_1();}public static void _m_1234_1() { }}")

        when:
        mutator.beforeBuild(buildContext)

        then:
        parse(sourceFile) == parse("class Thing { public void existingMethod() { _m_1234_2();}public static void _m_1234_2() { }}")

        when:
        mutator.beforeBuild(buildContext)

        then:
        parse(sourceFile) == parse("class Thing { public void existingMethod() { _m_1234_3();}public static void _m_1234_3() { }}")
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

package org.gradle.profiler.mutations

class ApplyValueChangeToAndroidResourceFileMutatorTest extends AbstractMutatorTest {

    def "changes string resource at the end of source file"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = '<resources><string name="foo">bar</string></resources>'
        def mutator = new ApplyValueChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<resources><string name="foo">bar_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7</string></resources>'
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = '<resources><string name="foo">bar</string></resources>'
        def mutator = new ApplyValueChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == '<resources><string name="foo">bar</string></resources>'
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = '<resources><string name="foo">bar</string></resources>'
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == '<resources><string name="foo">bar</string></resources>'
    }
}

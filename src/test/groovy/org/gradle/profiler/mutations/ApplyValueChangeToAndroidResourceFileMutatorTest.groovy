package org.gradle.profiler.mutations

class ApplyValueChangeToAndroidResourceFileMutatorTest extends AbstractMutatorTest {

    def "changes string resource at the end of source file"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = '<resources><string name="foo">bar</string></resources>'
        def mutator = new ApplyValueChangeToAndroidResourceFileMutator(sourceFile)
        mutator.timestamp = 1234

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<resources><string name="foo">bar_1234_1</string></resources>'

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<resources><string name="foo">bar_1234_2</string></resources>'

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<resources><string name="foo">bar_1234_3</string></resources>'
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

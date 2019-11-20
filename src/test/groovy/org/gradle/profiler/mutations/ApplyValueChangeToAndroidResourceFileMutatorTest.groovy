package org.gradle.profiler.mutations

class ApplyValueChangeToAndroidResourceFileMutatorTest extends AbstractMutatorTest {

    def "changes string resource at the end of source file"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = '<resources><string name="foo">bar</string></resources>'
        def mutator = new ApplyValueChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<resources><string name="foo">barUNIQUE_ID</string></resources>'
        1 * buildContext.uniqueBuildId >> "UNIQUE_ID"
        0 * _
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

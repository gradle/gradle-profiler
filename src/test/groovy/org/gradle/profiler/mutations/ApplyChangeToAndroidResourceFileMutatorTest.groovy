package org.gradle.profiler.mutations

class ApplyChangeToAndroidResourceFileMutatorTest extends AbstractMutatorTest {

    def "adds string resource to end of source file"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<resources></resources>"
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)
        mutator.timestamp = 1234

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<resources><string name="new_resource">_1234_1</string></resources>'

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<resources><string name="new_resource">_1234_2</string></resources>'

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<resources><string name="new_resource">_1234_3</string></resources>'
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<resources></resources>"
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "<resources></resources>"
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<resources></resources>"
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "<resources></resources>"
    }
}

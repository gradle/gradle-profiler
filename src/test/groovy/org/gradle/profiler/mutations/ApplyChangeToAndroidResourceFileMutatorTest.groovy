package org.gradle.profiler.mutations

class ApplyChangeToAndroidResourceFileMutatorTest extends AbstractMutatorTest {

    def "adds string resource to end of source file"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<resources></resources>"
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<resources><string name="new_resource">UNIQUE_ID</string></resources>'
        1 * buildContext.uniqueBuildId >> "UNIQUE_ID"
        0 * _
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

package org.gradle.profiler.mutations

class ApplyChangeToAndroidResourceFileMutatorTest extends AbstractMutatorTest {

    def "adds string resource to end of source file"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<resources></resources>"
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<resources><string name="new_resource">_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7</string></resources>'
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

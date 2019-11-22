package org.gradle.profiler.mutations

class ApplyChangeToPropertyResourceFileMutatorTest extends AbstractMutatorTest {
    public static final String ORIGINAL_CONTENTS = "org.foo=bar"

    def "adds and removes property to end of source file"() {
        def sourceFile = tmpDir.newFile("test.properties")
        sourceFile.text = ORIGINAL_CONTENTS
        def mutator = new ApplyChangeToPropertyResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == 'org.foo=bar\norg.acme.some=_276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7\n'
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("test.properties")
        sourceFile.text = ORIGINAL_CONTENTS
        def mutator = new ApplyChangeToPropertyResourceFileMutator(sourceFile)

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == ORIGINAL_CONTENTS
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("test.properties")
        sourceFile.text = ORIGINAL_CONTENTS
        def mutator = new ApplyChangeToPropertyResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == ORIGINAL_CONTENTS
    }
}

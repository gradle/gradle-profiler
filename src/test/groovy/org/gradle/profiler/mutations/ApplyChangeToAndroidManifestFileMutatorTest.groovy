package org.gradle.profiler.mutations

class ApplyChangeToAndroidManifestFileMutatorTest extends AbstractMutatorTest {

    def "adds fake permission to end of android manifest file"() {
        def sourceFile = tmpDir.newFile("AndroidManifest.xml")
        sourceFile.text = "<manifest></manifest>"
        def mutator = new ApplyChangeToAndroidManifestFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<manifest><!-- _276d92f3_16ac_4064_9a18_5f1dfd67992f_testScenario_3c4925d7_MEASURE_7 --><permission android:name="com.acme.SOME_PERMISSION"/></manifest>'
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<manifest></manifest>"
        def mutator = new ApplyChangeToAndroidManifestFileMutator(sourceFile)

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "<manifest></manifest>"
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<manifest></manifest>"
        def mutator = new ApplyChangeToAndroidManifestFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == "<manifest></manifest>"
    }
}

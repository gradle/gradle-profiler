package org.gradle.profiler.mutations

class ApplyChangeToAndroidManifestFileMutatorTest extends AbstractMutatorTest {

    def "adds fake permission to end of android manifest file"() {
        def sourceFile = tmpDir.newFile("AndroidManifest.xml")
        sourceFile.text = "<manifest></manifest>"
        def mutator = new ApplyChangeToAndroidManifestFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == '<manifest><!-- UNIQUE_ID --><permission android:name="com.acme.SOME_PERMISSION"/></manifest>'
        1 * buildContext.uniqueBuildId >> "UNIQUE_ID"
        0 * _
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

package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApplyChangeToAndroidManifestFileMutatorTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    def "adds fake permission to end of android manifest file"() {
        def sourceFile = tmpDir.newFile("AndroidManifest.xml")
        sourceFile.text = "<manifest></manifest>"
        def mutator = new ApplyChangeToAndroidManifestFileMutator(sourceFile)
        mutator.timestamp = 1234

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == '<manifest><!-- _1234_1 --><permission android:name="com.acme.SOME_PERMISSION"/></manifest>'

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == '<manifest><!-- _1234_2 --><permission android:name="com.acme.SOME_PERMISSION"/></manifest>'

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == '<manifest><!-- _1234_3 --><permission android:name="com.acme.SOME_PERMISSION"/></manifest>'
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<manifest></manifest>"
        def mutator = new ApplyChangeToAndroidManifestFileMutator(sourceFile)

        when:
        mutator.afterScenario()

        then:
        sourceFile.text == "<manifest></manifest>"
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<manifest></manifest>"
        def mutator = new ApplyChangeToAndroidManifestFileMutator(sourceFile)

        when:
        mutator.beforeBuild()
        mutator.afterScenario()

        then:
        sourceFile.text == "<manifest></manifest>"
    }
}

package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApplyChangeToAndroidResourceFileMutatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "adds and removes string resource to end of source file"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<resources></resources>"
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == '<resources><string name="new_resource">some value</string></resources>'

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == "<resources></resources>"

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == '<resources><string name="new_resource">some value</string></resources>'
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<resources></resources>"
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.cleanup()

        then:
        sourceFile.text == "<resources></resources>"
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<resources></resources>"
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild()
        mutator.cleanup()

        then:
        sourceFile.text == "<resources></resources>"
    }
}

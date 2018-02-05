package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApplyChangeToAndroidResourceFileMutatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "adds string resource to end of source file"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<resources></resources>"
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)
        mutator.timestamp = 1234

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == '<resources><string name="new_resource">_1234_1</string></resources>'

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == '<resources><string name="new_resource">_1234_2</string></resources>'

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == '<resources><string name="new_resource">_1234_3</string></resources>'
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<resources></resources>"
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.afterScenario()

        then:
        sourceFile.text == "<resources></resources>"
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = "<resources></resources>"
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild()
        mutator.afterScenario()

        then:
        sourceFile.text == "<resources></resources>"
    }
}

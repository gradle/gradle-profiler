package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApplyValueChangeToAndroidResourceFileMutatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "changes string resource at the end of source file"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = '<resources><string name="foo">bar</string></resources>'
        def mutator = new ApplyValueChangeToAndroidResourceFileMutator(sourceFile)
        mutator.timestamp = 1234

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == '<resources><string name="foo">bar_1234_1</string></resources>'

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == '<resources><string name="foo">bar_1234_2</string></resources>'

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == '<resources><string name="foo">bar_1234_3</string></resources>'
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = '<resources><string name="foo">bar</string></resources>'
        def mutator = new ApplyValueChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.cleanup()

        then:
        sourceFile.text == '<resources><string name="foo">bar</string></resources>'
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("strings.xml")
        sourceFile.text = '<resources><string name="foo">bar</string></resources>'
        def mutator = new ApplyChangeToAndroidResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild()
        mutator.cleanup()

        then:
        sourceFile.text == '<resources><string name="foo">bar</string></resources>'
    }
}

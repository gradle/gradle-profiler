package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApplyNonAbiChangeToJavaSourceFileMutatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "adds and changes public method to end of source file"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { }"
        def mutator = new ApplyNonAbiChangeToJavaSourceFileMutator(sourceFile)
        mutator.timestamp = 1234

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == 'class Thing { public void _m_1234() { System.out.println("_1234_1"); }}'

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == 'class Thing { public void _m_1234() { System.out.println("_1234_2"); }}'

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == 'class Thing { public void _m_1234() { System.out.println("_1234_3"); }}'
    }
}

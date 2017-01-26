package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApplyNonAbiChangeToJavaSourceFileMutatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "changes the last method in the source file"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { public void existingMethod() { }}"
        def mutator = new ApplyNonAbiChangeToJavaSourceFileMutator(sourceFile)
        mutator.timestamp = 1234

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == 'class Thing { public void existingMethod() { System.out.println("_1234_1");}}'

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == 'class Thing { public void existingMethod() { System.out.println("_1234_2");}}'

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == 'class Thing { public void existingMethod() { System.out.println("_1234_3");}}'
    }

    def "does not work with Java files that do not contain a method"() {
        def sourceFile = tmpDir.newFile("Thing.java")
        sourceFile.text = "class Thing { }"
        def mutator = new ApplyNonAbiChangeToJavaSourceFileMutator(sourceFile)
        mutator.timestamp = 1234

        when:
        mutator.beforeBuild()

        then:
        IllegalArgumentException t = thrown()
        t.message == "Cannot parse source file " + sourceFile + " to apply changes"
    }
}

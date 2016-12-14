package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApplyChangeToPropertyFileMutatorTest extends Specification {
    public static final String ORIGINAL_CONTENTS = "org.foo=bar"
    public static final String MODIFIED_CONTENTS = 'org.foo=bar\norg.acme.some=thing\n'
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "adds and removes property to end of source file"() {
        def sourceFile = tmpDir.newFile("test.properties")
        sourceFile.text = ORIGINAL_CONTENTS
        def mutator = new ApplyChangeToPropertyResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == MODIFIED_CONTENTS

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == ORIGINAL_CONTENTS

        when:
        mutator.beforeBuild()

        then:
        sourceFile.text == MODIFIED_CONTENTS
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("test.properties")
        sourceFile.text = ORIGINAL_CONTENTS
        def mutator = new ApplyChangeToPropertyResourceFileMutator(sourceFile)

        when:
        mutator.cleanup()

        then:
        sourceFile.text == ORIGINAL_CONTENTS
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("test.properties")
        sourceFile.text = ORIGINAL_CONTENTS
        def mutator = new ApplyChangeToPropertyResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild()
        mutator.cleanup()

        then:
        sourceFile.text == ORIGINAL_CONTENTS
    }
}

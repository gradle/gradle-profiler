package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class GitRevertMutatorTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    File projectDir

    def setup() {
        projectDir = tmpDir.newFolder()
    }

    def "reverts things properly"() {
        def file = new File(projectDir, "file.txt")
        file.text = "Original"
        ["git", "init"].execute([], projectDir).waitFor()
        ["git", "add", "-A"].execute([], projectDir).waitFor()
        ["git", "commit", "-m", "Initial import"].execute([], projectDir).waitFor()

        file.text = "Modified"
        ["git", "add", "-A"].execute([], projectDir).waitFor()
        ["git", "commit", "-m", "Modified"].execute([], projectDir).waitFor()

        file.text = "Local change"

        def mutator = new GitRevertMutator(projectDir, ["HEAD"])

        when:
        mutator.beforeScenario()
        then:
        file.text == "Modified"

        when:
        mutator.beforeCleanup()
        then:
        file.text == "Modified"

        when:
        mutator.beforeBuild()
        then:
        file.text == "Original"

        when:
        mutator.afterBuild()
        then:
        file.text == "Modified"

        when:
        mutator.afterScenario()
        then:
        file.text == "Modified"
    }
}

package org.gradle.profiler.mutations

import org.gradle.profiler.TestGitRepo
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class GitRevertMutatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "reverts things properly"() {
        def repo = new TestGitRepo(tmpDir.newFolder())
        def mutator = new GitRevertMutator(repo.directory, [repo.finalCommit, repo.modifiedCommit])

        when:
        mutator.beforeScenario()
        then:
        repo.atFinalCommit()
        repo.hasFinalContent()

        when:
        mutator.beforeCleanup()
        then:
        repo.atFinalCommit()
        repo.hasFinalContent()

        when:
        mutator.afterCleanup()
        then:
        repo.atFinalCommit()
        repo.hasFinalContent()

        when:
        mutator.beforeBuild()
        then:
        repo.atFinalCommit()
        repo.hasOriginalContent()

        when:
        mutator.afterBuild(new RuntimeException("Error"))
        then:
        repo.atFinalCommit()
        repo.hasOriginalContent()

        when:
        mutator.afterBuild(null)
        then:
        repo.atFinalCommit()
        repo.hasFinalContent()

        when:
        mutator.afterScenario()
        then:
        repo.atFinalCommit()
        repo.hasFinalContent()
    }
}

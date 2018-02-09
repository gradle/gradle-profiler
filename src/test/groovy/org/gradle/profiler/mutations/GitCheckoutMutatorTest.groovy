package org.gradle.profiler.mutations

import org.gradle.profiler.TestGitRepo
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class GitCheckoutMutatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "checks out target commit"() {
        def repo = new TestGitRepo(tmpDir.newFolder())
        def mutator = new GitCheckoutMutator(repo.directory, repo.modifiedCommit, repo.originalCommit)

        when:
        mutator.beforeScenario()
        then:
        repo.atFinalCommit()

        when:
        mutator.beforeCleanup()
        then:
        repo.atModifiedCommit()

        when:
        mutator.afterCleanup()
        then:
        repo.atModifiedCommit()

        when:
        mutator.beforeBuild()
        then:
        repo.atOriginalCommit()

        when:
        mutator.afterBuild(new RuntimeException("Error"))
        then:
        repo.atOriginalCommit()

        when:
        mutator.afterBuild(null)
        then:
        repo.atFinalCommit()

        when:
        mutator.afterScenario()
        then:
        repo.atFinalCommit()
    }
}

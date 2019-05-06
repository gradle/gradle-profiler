package org.gradle.profiler.mutations

import org.gradle.profiler.CommandExec
import org.gradle.profiler.TestGitRepo
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class GitCommitMutatorTest extends Specification {
    @Rule TemporaryFolder tempDir = new TemporaryFolder()

    def "should fail when unsaved changes"() {
        given:
        def repo = new TestGitRepo(tempDir.newFolder())
        def mutator = new GitCommitMutator(repo.directory, repo.modifiedCommit)

        when:
        mutator.beforeScenario()

        then:
        thrown(UnsupportedOperationException)
    }

    def "check out target commit"() {
        given:
        def repo = new TestGitRepo(tempDir.newFolder())
        def mutator = new GitCommitMutator(repo.directory, repo.modifiedCommit)
        new CommandExec().inDir(repo.directory).run("git", "reset", "--hard", "HEAD")

        when:
        mutator.beforeScenario()

        then:
        repo.atModifiedCommit()

        and:
        mutator.afterScenario()

        then:
        repo.atFinalCommit()
    }
}

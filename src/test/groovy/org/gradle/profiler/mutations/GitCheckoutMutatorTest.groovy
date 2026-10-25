package org.gradle.profiler.mutations

import org.gradle.profiler.TestGitRepo

class GitCheckoutMutatorTest extends AbstractMutatorTest {

    def "checks out target commit"() {
        def repo = new TestGitRepo(tmpDir.newFolder())
        new File(repo.directory, "file.txt").text = "Final"
        def mutator = new GitCheckoutMutator(repo.directory, repo.modifiedCommit, repo.originalCommit)

        when:
        mutator.beforeScenario(scenarioContext)
        then:
        repo.atFinalCommit()

        when:
        mutator.beforeCleanup(buildContext)
        then:
        repo.atModifiedCommit()

        when:
        mutator.afterCleanup(buildContext, null)
        then:
        repo.atModifiedCommit()

        when:
        mutator.beforeBuild(buildContext)
        then:
        repo.atOriginalCommit()

        when:
        mutator.afterBuild(buildContext, new RuntimeException("Error"))
        then:
        repo.atOriginalCommit()

        when:
        mutator.afterBuild(buildContext, null)
        then:
        repo.atFinalCommit()

        when:
        mutator.afterScenario(scenarioContext)
        then:
        repo.atFinalCommit()
    }

    def "checks out target commit and has no cleanup commit"() {
        given:
        def repo = new TestGitRepo(tmpDir.newFolder())
        new File(repo.directory, "file.txt").text = "Final"
        def mutator = new GitCheckoutMutator(repo.directory, null, repo.getOriginalCommit())

        when:
        mutator.beforeScenario(scenarioContext)
        then:
        repo.atFinalCommit()

        when:
        mutator.beforeCleanup(buildContext)
        then:
        repo.atFinalCommit()

        when:
        mutator.afterCleanup(buildContext, null)
        then:
        repo.atFinalCommit()

        when:
        mutator.beforeBuild(buildContext)
        then:
        repo.atOriginalCommit()

        when:
        mutator.afterBuild(buildContext, new RuntimeException("Error"))
        then:
        repo.atOriginalCommit()

        when:
        mutator.afterBuild(buildContext, null)
        then:
        repo.atFinalCommit()

        when:
        mutator.afterScenario(scenarioContext)
        then:
        repo.atFinalCommit()
    }

    def "rejects local changes without modifying the repository"() {
        given:
        def repo = new TestGitRepo(tmpDir.newFolder())
        def mutator = new GitCheckoutMutator(repo.directory, repo.originalCommit, repo.modifiedCommit)

        when:
        mutator.beforeScenario(scenarioContext)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot start Git checkout profiling with local changes. Commit, stash, or deliberately reset your changes and retry."
        repo.atFinalCommit()
        new File(repo.directory, "file.txt").text == "Local change"
    }

}

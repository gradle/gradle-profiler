package org.gradle.profiler.mutations

import org.gradle.profiler.TestGitRepo

class GitRevertMutatorTest extends AbstractMutatorTest {

    def "reverts things properly"() {
        def repo = new TestGitRepo(tmpDir.newFolder())
        def mutator = new GitRevertMutator(repo.directory, [repo.finalCommit, repo.modifiedCommit])

        when:
        mutator.beforeScenario(scenarioContext)
        then:
        repo.atFinalCommit()
        repo.hasFinalContent()

        when:
        mutator.beforeCleanup(buildContext)
        then:
        repo.atFinalCommit()
        repo.hasFinalContent()

        when:
        mutator.afterCleanup(buildContext, null)
        then:
        repo.atFinalCommit()
        repo.hasFinalContent()

        when:
        mutator.beforeBuild(buildContext)
        then:
        repo.atFinalCommit()
        repo.hasOriginalContent()

        when:
        mutator.afterBuild(buildContext, new RuntimeException("Error"))
        then:
        repo.atFinalCommit()
        repo.hasOriginalContent()

        when:
        mutator.afterBuild(buildContext, null)
        then:
        repo.atFinalCommit()
        repo.hasFinalContent()

        when:
        mutator.afterScenario(scenarioContext)
        then:
        repo.atFinalCommit()
        repo.hasFinalContent()
    }
}

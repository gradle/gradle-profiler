package org.gradle.profiler.mutations

import static org.gradle.profiler.mutations.AbstractCleanupMutator.CleanupSchedule.BUILD
import static org.gradle.profiler.mutations.AbstractCleanupMutator.CleanupSchedule.CLEANUP
import static org.gradle.profiler.mutations.AbstractCleanupMutator.CleanupSchedule.SCENARIO

class ClearBuildCacheMutatorTest extends AbstractMutatorTest {

    File gradleUserHome
    File buildCache1Entry
    File buildCache2Entry

    def setup() {
        gradleUserHome = tmpDir.newFolder()
        buildCache1Entry = new File(gradleUserHome, "caches/build-cache-1/12345678901234567890123456789012")
        buildCache2Entry = new File(gradleUserHome, "caches/build-cache-2/12345678901234567890123456789012")
    }

    private void remakeCacheEntries() {
        buildCache1Entry.parentFile.mkdirs()
        buildCache1Entry.createNewFile()
        buildCache2Entry.parentFile.mkdirs()
        buildCache2Entry.createNewFile()
    }

    def "clears build caches before scenario starts"() {
        def mutator = new ClearBuildCacheMutator(gradleUserHome, SCENARIO)

        when:
        remakeCacheEntries()
        mutator.beforeScenario(scenarioContext)
        then:
        !buildCache1Entry.exists()
        !buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeCleanup(buildContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterCleanup(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeBuild(buildContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterBuild(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterScenario(scenarioContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()
    }

    def "clears build caches before cleanup starts"() {
        def mutator = new ClearBuildCacheMutator(gradleUserHome, CLEANUP)

        when:
        remakeCacheEntries()
        mutator.beforeScenario(scenarioContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeCleanup(buildContext)
        then:
        !buildCache1Entry.exists()
        !buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterCleanup(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeBuild(buildContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterBuild(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterScenario(scenarioContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()
    }

    def "clears build caches before build starts"() {
        def mutator = new ClearBuildCacheMutator(gradleUserHome, BUILD)

        when:
        remakeCacheEntries()
        mutator.beforeScenario(scenarioContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeCleanup(buildContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterCleanup(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeBuild(buildContext)
        then:
        !buildCache1Entry.exists()
        !buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterBuild(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterScenario(scenarioContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()
    }
}

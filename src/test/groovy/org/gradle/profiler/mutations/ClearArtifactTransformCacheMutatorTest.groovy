package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification


class ClearArtifactTransformCacheMutatorTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    def "removes directories with transforms prefix"() {
        def userHome = tmpDir.newFolder()
        def cachesDir = new File(userHome, "caches")
        cachesDir.mkdirs()
        def file = new File(cachesDir, "transforms-1")
        file.text = "a file"
        def dir = new File(cachesDir, "transforms-2/files-1/thing")
        dir.mkdirs()
        def ignored1 = new File(cachesDir, "keep-me/files-1/thing")
        ignored1.mkdirs()
        def ignored2 = new File(cachesDir, "transforms/files-1/thing")
        ignored2.mkdirs()

        when:
        def mutator = new ClearArtifactTransformCacheMutator(userHome, AbstractCleanupMutator.CleanupSchedule.BUILD)
        mutator.beforeBuild()

        then:
        file.file
        !dir.exists()
        ignored1.directory
        ignored2.directory
    }
}

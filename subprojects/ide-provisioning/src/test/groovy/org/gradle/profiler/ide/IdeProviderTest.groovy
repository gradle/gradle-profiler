package org.gradle.profiler.ide


import org.gradle.profiler.ide.idea.IDEA
import org.gradle.profiler.ide.idea.IDEAProvider

class IdeProviderTest extends AbstractIdeProvisioningTest {

    def "can provide IDEA"() {
        given:
        def workDir = tmpDir.newFolder().toPath().toAbsolutePath()
        def downloadsDir = workDir.resolve("downloads")
        def ideHomeDir = workDir.resolve("ide")
        def ideProvider = new DefaultIdeProvider(new IDEAProvider())

        when:
        def ide = ideProvider.provideIde(IDEA.LATEST, ideHomeDir, downloadsDir)

        then:
        outputContains("Downloading https://")
        ide.exists()

        when:
        def ide2 = ideProvider.provideIde(IDEA.LATEST, ideHomeDir, downloadsDir)

        then:
        outputContains("Downloading is skipped, get IDEA Community from cache")
        ide == ide2

        and:
        !downloadsDir.toFile().exists()
    }
}

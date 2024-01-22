package org.gradle.profiler.ide

import org.gradle.profiler.ide.idea.IDEA
import org.gradle.profiler.ide.studio.AndroidStudio

class IdeProviderTest extends AbstractIdeProvisioningTest {

    def "can provide #title"() {
        given:
        def workDir = tmpDir.newFolder().toPath().toAbsolutePath()
        def downloadsDir = workDir.resolve("downloads")
        def ideHomeDir = workDir.resolve("ide")
        def ideProvider = new DefaultIdeProvider()

        when:
        def ideFile = ideProvider.provideIde(ide, ideHomeDir, downloadsDir)

        then:
        outputContains("Downloading https://")
        ideFile.exists()

        when:
        def ideFile2 = ideProvider.provideIde(ide, ideHomeDir, downloadsDir)

        then:
        outputContains("Downloading is skipped, get $title from cache")
        ideFile == ideFile2

        and:
        !downloadsDir.toFile().exists()

        where:
        ide                              | title
        IDEA.LATEST                      | "IDEA Community"
        new AndroidStudio("2023.2.1.16") | "Android Studio"
    }
}

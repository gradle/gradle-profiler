package org.gradle.profiler

import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest
import org.gradle.profiler.studio.IdeType

class IdeSyncInstallDirMismatchIntegrationTest extends AbstractProfilerIntegrationTest {

    def "fails when #scenarioBlock scenario points to the other IDE's install dir"() {
        given:
        // User configured a $scenarioBlock scenario but pointed to an installation of the other IDE.
        def installDir = tmpDir.createDir("wrong-ide-install")
        createStarter(installDir, wrongIde)
        def scenarioFile = file("performance.scenarios") << """
            scenario {
                $scenarioBlock {}
            }
        """

        when:
        run([
            "--benchmark",
            "--scenario-file", scenarioFile.absolutePath,
            installDirOption, installDir.absolutePath,
            "scenario"
        ])

        then:
        def e = thrown(Main.ScenarioFailedException)
        e.cause.message.startsWith("Expected ${expectedIde.displayName} installation at ${installDir.absolutePath}")
        e.cause.message.contains("but no starter executable found at any of: ")

        where:
        scenarioBlock         | installDirOption       | expectedIde             | wrongIde
        "android-studio-sync" | "--studio-install-dir" | IdeType.ANDROID_STUDIO  | IdeType.INTELLIJ_IDEA
        "intellij-idea-sync"  | "--idea-install-dir"   | IdeType.INTELLIJ_IDEA   | IdeType.ANDROID_STUDIO
    }

    private static void createStarter(File installDir, IdeType ideType) {
        def relativePath = ideType.starterPathsForCurrentOs.first()
        def executable = new File(installDir, relativePath)
        executable.parentFile.mkdirs()
        executable.createNewFile()
    }
}

package org.gradle.profiler

import org.gradle.profiler.studio.LaunchConfiguration
import org.gradle.profiler.studio.LauncherConfigurationParser
import org.gradle.profiler.studio.tools.StudioFinder
import org.gradle.profiler.studio.tools.StudioSandboxCreator
import org.gradle.profiler.studio.tools.StudioLauncher
import spock.lang.Requires

class AndroidStudioIntegrationTest extends AbstractProfilerIntegrationTest {

    @Requires({ StudioFinder.findStudioHome() })
    def "benchmarks Android Studio sync with latest gradle version"() {
        given:
        File sandbox = tmpDir.newFolder('sandbox')
        File studioHome = StudioFinder.findStudioHome()
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                android-studio-sync {
                }
            }
        """

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--benchmark",
            "--scenario-file", scenarioFile.getAbsolutePath(),
            "--studio-install-dir", studioHome.absolutePath,
            "--studio-sandbox-dir", sandbox.absolutePath,
            "--warmups", "2",
            "--iterations", "3")

        then:
        logFile.find("Gradle invocation has completed in").size() == 5
        logFile.find("Full sync has completed in").size() == 5
        logFile.find("and it succeeded").size() == 5

        and:
        File benchmarkCsv = outputDir.listFiles().find { it.name.matches("benchmark.csv") }
        benchmarkCsv.text.contains("value,total execution time,Gradle execution time,IDE execution time")
    }

    @Requires({ StudioFinder.findStudioHome() })
    def "detects if two Android Studio processes are running in the same sandbox"() {
        given:
        File sandboxDir = tmpDir.newFolder('sandbox')
        File studioHome = StudioFinder.findStudioHome()
        File otherStudioProjectDir = tmpDir.newFolder('project')
        StudioSandboxCreator.StudioSandbox sandbox = StudioSandboxCreator.createSandbox(sandboxDir.toPath())
        LaunchConfiguration launchConfiguration = new LauncherConfigurationParser(studioHome.toPath(), sandbox).calculate()
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                android-studio-sync {
                }
            }
        """

        when:
        CommandExec.RunHandle process = StudioLauncher.launchStudio(launchConfiguration, otherStudioProjectDir)
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--benchmark",
            "--scenario-file", scenarioFile.getAbsolutePath(),
            "--studio-install-dir", studioHome.absolutePath,
            "--studio-sandbox-dir", sandboxDir.absolutePath,
            "--warmups", "1",
            "--iterations", "1")

        then:
        def e = thrown(Main.ScenarioFailedException)
        e.getCause().message == "Timeout waiting for incoming connection from plugin."
        logFile.containsOne("* ERROR")
        logFile.containsOne("* Could not connect to Android Studio process started by the gradle-profiler.")
        logFile.containsOne("* This might indicate that you are already running an Android Studio process in the same sandbox.")
        logFile.containsOne("* Stop Android Studio manually in the used sandbox or use a different sandbox with --studio-sandbox-dir to isolate the process.")

        cleanup:
        process.kill()
    }

    @Requires({ StudioFinder.findStudioHome() })
    def "allows two Android Studio processes in different sandboxes"() {
        given:
        File sandboxDir1 = tmpDir.newFolder('sandbox1')
        File sandboxDir2 = tmpDir.newFolder('sandbox2')
        // We create a different folder for the other process,
        // since if Android Studio writes to same project at the same time, it can fail
        File otherStudioProjectDir = tmpDir.newFolder('project')
        File studioHome = StudioFinder.findStudioHome()
        StudioSandboxCreator.StudioSandbox sandbox = StudioSandboxCreator.createSandbox(sandboxDir1.toPath())
        LaunchConfiguration launchConfiguration = new LauncherConfigurationParser(studioHome.toPath(), sandbox).calculate()
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                android-studio-sync {
                }
            }
        """

        when:
        CommandExec.RunHandle process = StudioLauncher.launchStudio(launchConfiguration, otherStudioProjectDir)
        new Main().run(
            "--project-dir", projectDir.absolutePath,
            "--output-dir", outputDir.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--benchmark",
            "--scenario-file", scenarioFile.getAbsolutePath(),
            "--studio-install-dir", studioHome.absolutePath,
            "--studio-sandbox-dir", sandboxDir2.absolutePath,
            "--warmups", "1",
            "--iterations", "1")

        then:
        logFile.find("Gradle invocation has completed in").size() == 2
        logFile.find("Full sync has completed in").size() == 2

        cleanup:
        process.kill()
    }

}

package org.gradle.profiler

import org.gradle.profiler.studio.tools.StudioFinder
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
        logFile.find("* Cleaning Android Studio cache, this will require a restart...").size() == 0
        logFile.find("* Starting Android Studio").size() == 1

        and:
        File benchmarkCsv = outputDir.listFiles().find { it.name.matches("benchmark.csv") }
        benchmarkCsv.text.contains("value,total execution time,Gradle execution time,IDE execution time")
    }

    @Requires({ StudioFinder.findStudioHome() })
    def "benchmarks Android Studio sync by cleaning ide cache"() {
        given:
        File sandbox = tmpDir.newFolder('sandbox')
        File studioHome = StudioFinder.findStudioHome()
        def scenarioFile = file("performance.scenarios") << """
            s1 {
                android-studio-sync {
                    clean-ide-cache-before-sync = true
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
            "--warmups", "1",
            "--iterations", "2")

        then:
        logFile.find("Gradle invocation has completed in").size() == 3
        logFile.find("Full sync has completed in").size() == 3
        logFile.find("and it succeeded").size() == 3
        logFile.find("* Cleaning Android Studio cache, this will require a restart...").size() == 3
        // 4 since on first run we start IDE, clean cache and restart the IDE
        logFile.find("* Starting Android Studio").size() == 4
    }

}

package org.gradle.profiler

import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest
import spock.lang.Shared
import spock.lang.TempDir

class IdeaSyncGradleCrossVersionTest extends AbstractProfilerIntegrationTest {

    @Shared
    @TempDir
    File ideaHome

    private final String ideaVersion = "2025.2.3"

    def "benchmarks IDEA sync with cleaning IDE cache #cleanUpMode"() {
        given:
        def scenarioFile = file("performance.scenarios") << """
            ideaSync {
                idea-sync {}
                $scenarioOption
            }
        """

        when:
        iterations = 2
        runSyncBenchmark(scenarioFile)

        then:
        logFile.find("* Launching IDEA $ideaVersion").size() == ideaLaunchCount

        and:
        resultFile.find("warm-up build").size() == 1
        resultFile.find("measured build").size() == 2

        where:
        scenarioOption                              | cleanUpMode       | ideaLaunchCount
        "clear-android-studio-cache-before = BUILD" | "before build"    | 3
        ""                                          | "before scenario" | 1
    }

    private def runSyncBenchmark(File scenarioFile) {
        run([
            "--benchmark",
            "--scenario-file", scenarioFile.absolutePath,
            "--gradle-version", latestSupportedGradleVersion,
            "--idea-version", ideaVersion,
            "--idea-home", ideaHome.absolutePath,
            "--idea-sandbox", outputDir.absolutePath,
        ])
    }
}

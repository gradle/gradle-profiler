package org.gradle.profiler

import org.gradle.profiler.studio.tools.StudioFinder
import spock.lang.Requires

class AndroidStudioIntegrationTest extends AbstractProfilerIntegrationTest {

    @Requires({ StudioFinder.findStudioHome() })
    def "benchmarks Android Studio sync with latest gradle version"() {
        given:
        File sandbox = File.createTempDir('android-studio-test', 'sandbox')
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
        benchmarkCsv.text.contains("value,execution,Gradle Tooling Agent execution time,Android Studio execution time")
    }

}

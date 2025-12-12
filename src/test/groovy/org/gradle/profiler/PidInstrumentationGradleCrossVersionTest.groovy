package org.gradle.profiler

import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest
import spock.lang.Requires

class PidInstrumentationGradleCrossVersionTest extends AbstractGradleCrossVersionTest {

    @Requires({ it.instance.gradleVersionWithExperimentalConfigurationCache() })
    def "pid instrumentation works for cold daemon with configuration caching"() {
        given:
        instrumentedBuildScript()
        file("gradle.properties") << """
            org.gradle.unsafe.configuration-cache=true
        """

        def scenarioFile = file("performance.scenarios") << """
            s1 {
                versions = ["$gradleVersion"]
                run-using = cli
                daemon = cold
                tasks = assemble
            }
        """

        when:
        warmups = null
        iterations = null
        run(["--benchmark", "--scenario-file", scenarioFile.absolutePath, "s1"])

        then:
        logFile.containsOne("Run using: `gradle` command with cold daemon")
        logFile.find("Configuration cache entry reused.").size() >= 1
        resultFile.containsColdDaemonScenario(gradleVersion, "s1", ["assemble"])
    }
}

package org.gradle.profiler

import org.gradle.profiler.fixtures.AbstractProfilerIntegrationTest
import org.gradle.profiler.fixtures.compatibility.gradle.GradleVersionCompatibility
import org.gradle.util.GradleVersion
import spock.lang.Requires

class UnsupportedConfigTimeBenchmarkIntegrationTest extends AbstractProfilerIntegrationTest {

    // Gradle version that does not support measuring configuration time
    @Requires({ GradleVersion.version(it.instance.minimalSupportedGradleVersion) < GradleVersionCompatibility.minimalGradleVersionWithAdvancedBenchmarking })
    def "complains when attempting to benchmark configuration time"() {
        given:
        instrumentedBuildScript()

        when:
        run(["--gradle-version", minimalSupportedGradleVersion, "--benchmark", "--measure-config-time", "assemble"])

        then:
        thrown(IllegalArgumentException)

        and:
        output.contains("Scenario using Gradle ${minimalSupportedGradleVersion}: Measuring build configuration is only supported for Gradle 6.1-milestone-3 and later")
    }

    @Requires({ it.instance.isCurrentJvmSupportsMultipleGradleVersions() })
    // Gradle version that does not support measuring configuration time
    @Requires({ GradleVersion.version(it.instance.minimalSupportedGradleVersion) < GradleVersionCompatibility.minimalGradleVersionWithAdvancedBenchmarking })
    def "complains when attempting to benchmark configuration time for build using unsupported Gradle version from scenario file"() {
        given:
        instrumentedBuildScript()
        def scenarioFile = file("performance.scenarios")
        scenarioFile.text = """
            assemble {
                versions = ["$minimalSupportedGradleVersion", "$latestSupportedGradleVersion"]
            }
        """

        when:
        run(["--scenario-file", scenarioFile.absolutePath, "--benchmark", "--measure-config-time", "assemble"])

        then:
        thrown(IllegalArgumentException)

        and:
        assert output.contains("Scenario assemble using Gradle ${minimalSupportedGradleVersion}: Measuring build configuration is only supported for Gradle 6.1-milestone-3 and later")
    }
}

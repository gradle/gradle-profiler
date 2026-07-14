package org.gradle.profiler.gradle

import org.gradle.profiler.fixtures.AbstractIntegrationTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DefaultGradleBuildConfigurationReaderTest extends AbstractIntegrationTest {

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()
    File projectDir
    File gradleUserHome
    DaemonControl daemonControl

    def setup() {
        projectDir = tmpDir.newFolder("test-project")
        gradleUserHome = tmpDir.newFolder("gradle-home")
        daemonControl = new DaemonControl(gradleUserHome)
    }

    def "configuration reader throw exception when gradle version is unsupported string"() {
        given:
        def reader = new DefaultGradleBuildConfigurationReader(projectDir, gradleUserHome, daemonControl)

        when:
        reader.readConfiguration(gradleVersion)

        then:
        IllegalArgumentException exception = thrown(IllegalArgumentException.class)
        exception.message == "Unrecognized Gradle version '$gradleVersion' specified."

        where:
        gradleVersion << ["some garbage string", "ftp://unsupported/distribution.zip", ""]
    }
}

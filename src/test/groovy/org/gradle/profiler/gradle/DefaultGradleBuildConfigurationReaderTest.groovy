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

    def "configuration reader throw exception when gradle version is some garbage string"() {
        given: "Given"
        def reader = new DefaultGradleBuildConfigurationReader(projectDir, gradleUserHome, daemonControl)

        when: "When"
        reader.readConfiguration("some garbage string")

        then: "Then"
        IllegalArgumentException exception = thrown(IllegalArgumentException.class)
        exception.message == "Unrecognized Gradle version 'some garbage string' specified."
    }

    def "configuration reader throw exception when gradle version is empty string"() {
        given: "Given"
        def reader = new DefaultGradleBuildConfigurationReader(projectDir, gradleUserHome, daemonControl)

        when: "When"
        reader.readConfiguration("")

        then: "Then"
        IllegalArgumentException exception = thrown(IllegalArgumentException.class)
        exception.message == "Unrecognized Gradle version '' specified."
    }
}

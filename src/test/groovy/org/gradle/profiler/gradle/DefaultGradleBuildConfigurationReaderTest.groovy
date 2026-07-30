package org.gradle.profiler.gradle

import org.gradle.profiler.fixtures.AbstractIntegrationTest
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.Launchable
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.GradleEnvironment
import org.gradle.tooling.model.build.JavaEnvironment
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DefaultGradleBuildConfigurationReaderTest extends AbstractIntegrationTest {

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()
    File projectDir
    File gradleUserHome

    def setup() {
        projectDir = tmpDir.newFolder("test-project")
        gradleUserHome = tmpDir.newFolder("gradle-home")
    }

    def "configuration reader throw exception when gradle version is unsupported string"() {
        given:
        def daemonControl = Mock(DaemonControl)
        def reader = new DefaultGradleBuildConfigurationReader(projectDir, gradleUserHome, daemonControl)

        when:
        reader.readConfiguration(gradleVersion)

        then:
        RuntimeException exception = thrown(RuntimeException.class)
        exception.message == "Unrecognized Gradle version '$gradleVersion' specified."

        where:
        gradleVersion << ["some garbage string", "ftp://unsupported/distribution.zip", ""]
    }

    def "configuration reader returns build config when gradle version is valid"() {
        given:
        def buildLauncher = Mock(BuildLauncher)
        buildLauncher.forTasks(_ as Iterable<? extends Task>) >> buildLauncher
        buildLauncher.forLaunchables(_ as Iterable<? extends Launchable>) >> buildLauncher
        def javaEnvironment = Mock(JavaEnvironment)
        javaEnvironment.getJavaHome() >> Mock(File)
        javaEnvironment.getJvmArguments() >> []
        def gradleEnvironment = Mock(GradleEnvironment)
        gradleEnvironment.gradleVersion >> expectedVersion
        def buildEnvironment = Mock(BuildEnvironment)
        buildEnvironment.getJava() >> javaEnvironment
        buildEnvironment.getGradle() >> gradleEnvironment
        def projectConnection = Mock(ProjectConnection)
        projectConnection.newBuild() >> buildLauncher
        projectConnection.getModel(_ as Class<Object>) >> buildEnvironment
        def gradleConnector = Mock(GradleConnector)
        gradleConnector.connect() >> projectConnection
        gradleConnector.useInstallation(_ as File) >> gradleConnector
        gradleConnector.useGradleVersion(_ as String) >> gradleConnector
        gradleConnector.useDistribution(_ as URI) >> gradleConnector
        gradleConnector.useBuildDistribution() >> gradleConnector
        gradleConnector.forProjectDirectory(_ as File) >> gradleConnector
        gradleConnector.useGradleUserHomeDir(_ as File) >> gradleConnector
        SpyStatic(GradleConnector)
        GradleConnector.newConnector() >> gradleConnector
        def daemonControl = Mock(DaemonControl)
        def reader = new DefaultGradleBuildConfigurationReader(projectDir, gradleUserHome, daemonControl)
        if (gradleVersion == "gradle-9-dir") {
            gradleVersion = tmpDir.newFolder(gradleVersion).path
        } else if (gradleVersion == "gradle-9.5.0.zip") {
            gradleVersion = tmpDir.newFile(gradleVersion).path
        }

        when:
        def result = reader.readConfiguration(gradleVersion)

        then:
        result.gradleVersion.version == expectedVersion

        where:
        gradleVersion                   || expectedVersion
        "gradle-9-dir"                  || "9.0.0"
        "9.6.1"                         || "9.6.1"
        "http://gradle-9.6.0-all.zip"   || "9.6.0"
        "https://gradle-9.4.1-bin.zip"  || "9.4.1"
        "file://gradle-9.2.1-bin.zip"   || "9.2.1"
        "gradle-9.5.0.zip"              || "9.5.0"
    }
}

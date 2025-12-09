package org.gradle.profiler

import spock.lang.Requires
import spock.util.environment.Jvm

// Ensure we are running something strictly above Java 8
@Requires({ Jvm.current.isJava11Compatible() })
class DaemonJavaVersionIntegrationTest extends AbstractProfilerIntegrationTest {

    def "can run benchmark via CLI with JVM different from Gradle Profiler and build-defined org.gradle.java.home"() {
        given:
        buildFile.text = """
            println "Build is running on Java '\${JavaVersion.current().majorVersion}'"
        """

        def javaHomeForJava8 = System.getProperty("javaHomes.java8")
        file("gradle.properties") << """
            org.gradle.java.home=${javaHomeForJava8}
        """

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath,
            "--gradle-version", gradleVersion,
            "--warmups", "1", "--iterations", "1",
            "--benchmark", "help"
        )

        then:
        // count 3 = 1 probe + 1 warm-up + 1 iteration
        logFile.find("Build is running on Java '8'").size() == 3

        where:
        gradleVersion << [minimalSupportedGradleVersion, latestJava8CompatibleDaemonGradleVersion]
    }

    def "can run benchmark via TAPI with JVM different from Gradle Profiler and build-defined org.gradle.java.home"() {
        given:
        def scenarioFile = file("benchmark.conf")
        scenarioFile.text = """
            ideaModel {
                versions = ["$minimalSupportedGradleVersion", "$latestJava8CompatibleDaemonGradleVersion"]
                tooling-api {
                    model = "org.gradle.tooling.model.idea.IdeaProject"
                }
            }
        """

        buildFile.text = """
            println "Build is running on Java '\${JavaVersion.current().majorVersion}'"
        """

        def javaHomeForJava8 = System.getProperty("javaHomes.java8")
        file("gradle.properties") << """
            org.gradle.java.home=${javaHomeForJava8}
        """

        when:
        new Main().run(
            "--project-dir", projectDir.absolutePath, "--output-dir", outputDir.absolutePath,
            "--warmups", "1", "--iterations", "1",
            "--scenario-file", scenarioFile.absolutePath,
            "--benchmark", "ideaModel"
        )

        then:
        // count 3 = 1 probe + 1 warm-up + 1 iteration
        // x2 due to 2 gradle versions in the scenario
        logFile.find("Build is running on Java '8'").size() == 3 * 2
    }
}

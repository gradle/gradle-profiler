package org.gradle.profiler

class IdeaIntegrationTest extends AbstractIdeIntegrationTest {

    def "benchmarks Java project IDEA sync"() {
        given:
        def scenario = scenario("""
            ide-type=IC
            ide-build-type=release
            ide-version=2023.2.3
            ide-home=${ideHome.absolutePath}
        """)

        simpleJavaProject()

        when:
        runBenchmark(scenario, 1, 1)

        then:
        logFile.find("Gradle invocation 1 has completed in").size() == 2
        logFile.find("Full sync has completed in").size() == 2
        logFile.find("and it SUCCEEDED").size() == 2
    }

    private void simpleJavaProject() {
        settingsFile << """
            rootProject.name = 'project-under-test'
            include ':app'
            include ':lib'
        """

        file("app").mkdirs()
        file("lib").mkdirs()

        file("app/build.gradle") << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation(project(':lib'))
            }
        """

        file("lib/build.gradle") << """
            plugins {
                id 'java'
            }
        """
    }
}

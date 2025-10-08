package org.gradle.profiler

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Unroll

@Unroll
class CommandLineIntegrationTest extends AbstractIntegrationTest {

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    def "can show help with #option"() {
        when:
        new Main().run(option)
        then:
        output =~ /-h, --help\s+Show this usage information/

        where:
        option << ["-h", "--help"]
    }

    def "can show version with #option"() {
        when:
        new Main().run(option)
        then:
        output =~ /Gradle Profiler version .*/

        where:
        option << ["-v", "--version"]
    }

    def "--dump-scenarios requires --scenario-file"() {
        when:
        new Main().run("--benchmark", "--dump-scenarios")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "--dump-scenarios requires a scenario file"
    }

    def "can dump simple scenario"() {
        given:
        def scenarioFile = tmpDir.newFile("test.conf")
        scenarioFile << """
            my-scenario {
                tasks = ["help"]
                warm-ups = 3
            }
        """

        when:
        new Main().run("--benchmark", "--scenario-file", scenarioFile.absolutePath, "--dump-scenarios", "my-scenario")

        then:
        output == """
# Scenario 1/1
my-scenario {
    tasks=[
        help
    ]
    warm-ups=3
}

"""
    }
}

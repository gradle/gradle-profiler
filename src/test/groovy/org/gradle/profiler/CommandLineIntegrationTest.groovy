package org.gradle.profiler

import spock.lang.Ignore
import spock.lang.Unroll

@Ignore
@Unroll
class CommandLineIntegrationTest extends AbstractIntegrationTest {
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
}

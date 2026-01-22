package org.gradle.profiler

import org.gradle.profiler.fixtures.compatibility.gradle.AbstractGradleCrossVersionTest

import java.text.NumberFormat

class ShowBuildCacheSizeMutatorGradleCrossVersionTest extends AbstractGradleCrossVersionTest {
    def "displays local build cache size"() {
        given:
        buildFile << """
            // Produce some output so the cache size is noticeable
            task producePayload() {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                outputs.cacheIf { true }
                doLast {
                    def buffer = new byte[1024 * 1024]
                    new Random().nextBytes(buffer)
                    outputFile.bytes = buffer
                }
            }
        """

        def scenarios = file("performance.scenario")
        scenarios.text = """
            buildTarget = {
                clear-build-cache-before = SCENARIO
                show-build-cache-size = true
                gradle-args = ["--build-cache"]
                tasks = ["producePayload"]
            }
        """

        when:
        run([
            "--gradle-version", gradleVersion,
            "--benchmark",
            "--scenario-file", scenarios.absolutePath,
            "-Dorg.gradle.caching=true"
        ])
        def matcher = output =~ /Build cache size: (?<size>.*) bytes in (?<count>.*) file\(s\) \(build-cache-.*\)/

        then:
        matcher.find()
        assert NumberFormat.getInstance().parse(matcher.group("size")) > 0
        assert NumberFormat.getInstance().parse(matcher.group("count")) > 0
    }
}

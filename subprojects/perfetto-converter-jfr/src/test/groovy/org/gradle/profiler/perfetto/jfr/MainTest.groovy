package org.gradle.profiler.perfetto.jfr

import org.gradle.profiler.perfetto.jfr.fixture.SyntheticRecording
import spock.lang.Specification
import spock.lang.TempDir

class MainTest extends Specification {
    @TempDir
    File temporaryDirectory

    def "converts to the default perfetto output path when only the JFR input is provided"() {
        given:
        File inputFile = new File(temporaryDirectory, "recording.jfr")
        File outputFile = new File(temporaryDirectory, "recording.perfetto")
        SyntheticRecording.writeConvertibleRecording(inputFile)

        when:
        int exitCode = Main.run([inputFile.absolutePath] as String[])

        then:
        exitCode == 0
        outputFile.isFile()
    }

    def "converts to an explicit output path when one is provided"() {
        given:
        File inputFile = new File(temporaryDirectory, "recording.jfr")
        File outputFile = new File(temporaryDirectory, "custom-output.perfetto")
        SyntheticRecording.writeConvertibleRecording(inputFile)

        when:
        int exitCode = Main.run([inputFile.absolutePath, outputFile.absolutePath] as String[])

        then:
        exitCode == 0
        outputFile.isFile()
    }

    def "rejects non-jfr input files"() {
        given:
        File inputFile = new File(temporaryDirectory, "not-a-recording.txt")
        inputFile.text = "nope"

        when:
        int exitCode = Main.run([inputFile.absolutePath] as String[])

        then:
        exitCode == 1
        !new File(temporaryDirectory, "not-a-recording.perfetto").exists()
    }

    def "rejects a missing input file"() {
        when:
        int exitCode = Main.run([new File(temporaryDirectory, "missing.jfr").absolutePath] as String[])

        then:
        exitCode == 1
    }

    def "rejects invalid argument counts"() {
        expect:
        Main.run(arguments as String[]) == 1

        where:
        arguments << [[], ["a.jfr", "out.perfetto", "extra"]]
    }
}

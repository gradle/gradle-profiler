package org.gradle.profiler.perfetto.jfr

import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.TempDir

class MainTest extends Specification {
    private static final String FIXTURE_NAME = "recording-cold-cc-hit.jfr"

    @TempDir
    File temporaryDirectory

    @Ignore("Fixture recording-cold-cc-hit.jfr was recorded by a newer JDK; jdk.jfr cannot parse it on JDK 17. Regenerate the fixture (or synthesize one at runtime) to re-enable.")
    def "converts to the default perfetto output path when only the JFR input is provided"() {
        given:
        File inputFile = copyFixtureToTemporaryDirectory()
        File outputFile = new File(temporaryDirectory, "recording-cold-cc-hit.perfetto")

        when:
        int exitCode = Main.run([inputFile.absolutePath] as String[])

        then:
        exitCode == 0
        outputFile.isFile()
    }

    @Ignore("Fixture recording-cold-cc-hit.jfr was recorded by a newer JDK; jdk.jfr cannot parse it on JDK 17. Regenerate the fixture (or synthesize one at runtime) to re-enable.")
    def "converts to an explicit output path when one is provided"() {
        given:
        File inputFile = copyFixtureToTemporaryDirectory()
        File outputFile = new File(temporaryDirectory, "custom-output.perfetto")

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

    private File copyFixtureToTemporaryDirectory() {
        File target = new File(temporaryDirectory, FIXTURE_NAME)
        target.bytes = fixtureBytes()
        return target
    }

    private byte[] fixtureBytes() {
        def resource = getClass().getResourceAsStream(FIXTURE_NAME)
        assert resource != null: "Missing test fixture ${FIXTURE_NAME}"
        try {
            return resource.readAllBytes()
        } finally {
            resource.close()
        }
    }
}

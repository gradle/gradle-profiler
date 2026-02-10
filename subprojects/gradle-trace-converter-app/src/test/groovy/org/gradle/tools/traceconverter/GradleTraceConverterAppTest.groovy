package org.gradle.tools.traceconverter

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class GradleTraceConverterAppTest extends Specification {

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    def stdout = new ByteArrayOutputStream()
    def stderr = new ByteArrayOutputStream()
    def originalOut = System.out
    def originalErr = System.err

    def setup() {
        System.out = new PrintStream(stdout)
        System.err = new PrintStream(stderr)
    }

    def cleanup() {
        System.out = originalOut
        System.err = originalErr
    }

    private static int run(String... args) {
        return AppKt.run(args)
    }

    private boolean outputContains(String text) {
        return stdout.toString().contains(text)
    }

    private boolean errorOutputContains(String text) {
        return stderr.toString().contains(text)
    }

    def "fails when no arguments are provided"() {
        when:
        def exitCode = run()

        then:
        exitCode == 1
        errorOutputContains("Usage: gtc <build-operations-trace-log>")
    }

    def "fails when file does not exist"() {
        given:
        def nonexistent = new File("/nonexistent/trace-log.txt")

        when:
        def exitCode = run(nonexistent.path)

        then:
        exitCode == 1
        errorOutputContains("File not found: ${nonexistent.absolutePath}")
    }

    def "converts sample trace log to perfetto proto"() {
        given:
        def sampleTrace = new File(getClass().getResource("/sample-trace-log.txt").toURI())
        def traceFile = tmpDir.newFile("sample-trace-log.txt")
        traceFile.bytes = sampleTrace.bytes

        when:
        def exitCode = run(traceFile.absolutePath)

        then:
        exitCode == 0
        def outputFile = new File(tmpDir.root, "sample-trace.perfetto.proto")
        outputFile.exists()
        outputFile.length() > 0
        outputContains(outputFile.absolutePath)
    }

}

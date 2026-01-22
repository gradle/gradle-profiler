package org.gradle.profiler.fixtures

import org.gradle.profiler.Logging
import org.gradle.profiler.TeeOutputStream
import spock.lang.Specification

class AbstractIntegrationTest extends Specification {
    ByteArrayOutputStream outputBuffer

    def setup() {
        Logging.resetLogging()
        outputBuffer = new ByteArrayOutputStream()
        System.out = new PrintStream(new TeeOutputStream(System.out, outputBuffer))
    }

    def cleanup() {
        Logging.resetLogging()
    }

    String getOutput() {
        System.out.flush()
        return new String(outputBuffer.toByteArray())
    }

}

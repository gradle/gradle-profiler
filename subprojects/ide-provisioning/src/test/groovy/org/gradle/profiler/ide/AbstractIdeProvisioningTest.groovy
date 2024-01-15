package org.gradle.profiler.ide

import org.gradle.profiler.TeeOutputStream
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class AbstractIdeProvisioningTest extends Specification {

    private ByteArrayOutputStream outputBuffer

    @Rule
    protected TemporaryFolder tmpDir = new TemporaryFolder(new File("build/tmp/"))

    def setup() {
        outputBuffer = new ByteArrayOutputStream()
        System.out = new PrintStream(new TeeOutputStream(System.out, outputBuffer))
    }

    protected void outputContains(String content) {
        assert getOutput().contains(content.trim())
    }

    protected String getOutput() {
        System.out.flush()
        return new String(outputBuffer.toByteArray())
    }
}

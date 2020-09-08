package org.gradle.profiler

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Requires
import spock.lang.Specification

@Requires({ !OperatingSystem.windows })
class CommandExecTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    void 'can direct output to a file'() {
        given:
        File output = new File(tmpDir.root, "output.txt")

        when:
        new CommandExec(tmpDir.root).runAndCollectOutput(output, "echo", "hello")

        then:
        output.text.trim() == 'hello'
    }
}

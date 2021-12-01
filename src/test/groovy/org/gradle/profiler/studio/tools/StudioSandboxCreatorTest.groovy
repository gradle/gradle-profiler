package org.gradle.profiler.studio.tools

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class StudioSandboxCreatorTest extends Specification {

    private static final String UPDATES_XML_PATH = "config/options/updates.xml";

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    def "should create config file with disabled updates"() {
        given:
        def sandbox = tmpDir.newFolder('sandbox')

        when:
        StudioSandboxCreator.createSandbox(sandbox.toPath())

        then:
        new File(sandbox, UPDATES_XML_PATH).text == """\
<application>
  <component name="UpdatesConfigurable">
    <option name="CHECK_NEEDED" value="false" />
  </component>
</application>
"""
    }

    def "should not overwrite updates config file if already exists"() {
        given:
        def sandbox = tmpDir.newFolder('sandbox')
        new File(sandbox, "config/options").mkdirs()
        def updatesXml = new File(sandbox, UPDATES_XML_PATH)
        updatesXml.text = "hello world"

        when:
        StudioSandboxCreator.createSandbox(sandbox.toPath())

        then:
        updatesXml.text == "hello world"
    }
}

package org.gradle.profiler.studio.plugin

import org.gradle.profiler.client.protocol.Server
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import spock.lang.Specification

class StudioPluginSpecification extends Specification {

    /**
     * Used so we can run integration tests as Spock tests and we use composition to setup the IDE.
     */
    @Rule
    TestRule runBareTestRule = { Statement base, Description description ->
        return new Statement() {
            @Override
            void evaluate() throws Throwable {
                server = new Server("plugin")
                System.setProperty("gradle.profiler.port", server.getPort() as String)
                new IdeSetupHelper(description, StudioPluginSpecification.this::createProject).runBare(base::evaluate)
            }
        }
    }

    Server server
    File buildFile
    File settingsFile

    def createProject(File projectDir) {
        projectDir.mkdirs()
        buildFile = new File(projectDir, "build.gradle")
        buildFile.createNewFile()
        settingsFile = new File(projectDir, "settings.gradle")
        settingsFile.createNewFile()
    }
}

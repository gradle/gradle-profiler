package org.gradle.profiler.studio.plugin

import org.gradle.profiler.client.protocol.Server
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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
    File gradleProperties

    def createProject(File projectDir) {
        projectDir.mkdirs()
        buildFile = new File(projectDir, "build.gradle")
        buildFile.createNewFile()
        settingsFile = new File(projectDir, "settings.gradle")
        settingsFile << "rootProject.name = 'test'"
        gradleProperties = new File(projectDir, "gradle.properties")
        gradleProperties << "org.gradle.jvmargs=-Xmx1024m"
        setupWrapper(projectDir.toPath())
    }

    private def setupWrapper(Path projectDir) {
        Files.createDirectories(projectDir.resolve("gradle/wrapper"))
        Files.copy(Paths.get("../../gradlew"), projectDir.resolve("gradlew"))
        Files.copy(Paths.get("../../gradlew.bat"), projectDir.resolve("gradlew.bat"))
        Files.copy(Paths.get("../../gradle/wrapper/gradle-wrapper.jar"), projectDir.resolve("gradle/wrapper/gradle-wrapper.jar"))
        Files.copy(Paths.get("../../gradle/wrapper/gradle-wrapper.properties"), projectDir.resolve("gradle/wrapper/gradle-wrapper.properties"))
    }
}

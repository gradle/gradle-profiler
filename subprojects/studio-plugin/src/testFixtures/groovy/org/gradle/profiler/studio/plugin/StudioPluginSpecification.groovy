package org.gradle.profiler.studio.plugin

import com.google.common.collect.ImmutableList
import org.gradle.profiler.client.protocol.Server
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class StudioPluginSpecification extends Specification {

    private static final List<String> WRAPPER_FILES = ImmutableList.of(
        "gradlew",
        "gradlew.bat",
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties"
    )

    @Rule
    TemporaryFolder tempDir = new TemporaryFolder();

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
                new IdeSetupHelper(description, tempDir, StudioPluginSpecification.this::createProject).runBare(base::evaluate)
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
        WRAPPER_FILES.each { Files.copy(Paths.get("../../$it"), projectDir.resolve(it)) }
    }
}

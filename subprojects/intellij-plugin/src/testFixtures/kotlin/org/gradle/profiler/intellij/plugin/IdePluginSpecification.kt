package org.gradle.profiler.intellij.plugin

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.internal.jvm.Jvm
import org.gradle.profiler.client.protocol.Server
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Base class for integration tests of the Gradle Profiler IntelliJ plugin.
 *
 * This class is in Kotlin (not Groovy) because Groovy 3's ASM parser cannot
 * read Java 21 bytecode – which IntelliJ Platform 2025.1+ is compiled to.
 */
@Suppress("UnconstructableJUnitTestCase")
abstract class IdePluginSpecification : HeavyPlatformTestCase() {

    private companion object {
        val WRAPPER_FILES = listOf(
            "gradlew",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties"
        )
    }

    private var jdk: Sdk? = null

    lateinit var server: Server
        private set

    lateinit var buildFile: File
    lateinit var settingsFile: File
    lateinit var gradleProperties: File

    /**
     * Test body must run off-EDT so blocking calls (waiting for IDE to respond)
     * do not deadlock the event dispatch thread.
     */
    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        // Create server before the EDT block – plain I/O, safe off-EDT.
        server = Server("plugin")
        System.setProperty("gradle.profiler.port", server.port.toString())

        // IntelliJ's setUp() and JDK/Gradle configuration must run on EDT.
        runInEdtAndWait {
            super.setUp()
            WriteAction.run<Exception> {
                jdk = JavaSdk.getInstance().createJdk("JDK", Jvm.current().javaHome.absolutePath)
                ProjectRootManager.getInstance(project).projectSdk = jdk
            }
            GradleSettings.getInstance(project).subscribe(
                object : ExternalSystemSettingsListener<GradleProjectSettings> {
                    override fun onProjectsLinked(settings: Collection<GradleProjectSettings>) {
                        settings.forEach { it.gradleJvm = ExternalSystemJdkUtil.USE_PROJECT_JDK }
                    }
                },
                GradleSettings.getInstance(project)
            )
        }
    }

    override fun tearDown() {
        try {
            server.close()
        } catch (_: Exception) {
        }
        runInEdtAndWait {
            WriteAction.run<Exception> {
                jdk?.let { ProjectJdkTable.getInstance().removeJdk(it) }
            }
            super.tearDown()
        }
    }

    /**
     * Opens the project from a freshly-created directory pre-populated with a
     * Gradle build and wrapper files by [createProject].
     *
     * Auto-import is disabled before the project opens so IntelliJ's Gradle plugin
     * cannot trigger a background sync before the test controls when to sync.
     * Our plugin manages syncs explicitly via the profiler protocol.
     */
    override fun doCreateAndOpenProject(): @NotNull Project {
        Registry.get("external.system.auto.import.disabled").setValue(true)
        val projectDir = tempDir.newPath()
        Files.createDirectories(projectDir)
        createProject(projectDir.toFile())
        return requireNotNull(
            com.intellij.openapi.project.ex.ProjectManagerEx.getInstanceEx()
                .openProject(projectDir, getOpenProjectOptions().build())
        )
    }

    /** Override to customise the Gradle project files created for each test. */
    protected open fun createProject(projectDir: File) {
        buildFile = File(projectDir, "build.gradle").also { it.createNewFile() }
        settingsFile = File(projectDir, "settings.gradle").also {
            it.writeText("rootProject.name = 'test'")
        }
        gradleProperties = File(projectDir, "gradle.properties").also {
            it.writeText("org.gradle.jvmargs=-Xmx1024m")
        }
        setupWrapper(projectDir)
    }

    private fun setupWrapper(projectDir: File) {
        val root = projectDir.toPath()
        Files.createDirectories(root.resolve("gradle/wrapper"))
        WRAPPER_FILES.forEach { Files.copy(Paths.get("../../$it"), root.resolve(it)) }
    }
}

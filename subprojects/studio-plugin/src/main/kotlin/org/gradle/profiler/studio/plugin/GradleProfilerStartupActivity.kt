package org.gradle.profiler.studio.plugin

import com.intellij.ide.impl.setTrusted
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import org.gradle.profiler.client.protocol.Client
import org.gradle.profiler.client.protocol.messages.IdeRequest
import org.gradle.profiler.client.protocol.messages.IdeRequest.IdeRequestType
import org.gradle.profiler.studio.plugin.client.GradleProfilerClient
import org.gradle.profiler.studio.plugin.system.IdeSystemHelper
import org.gradle.profiler.studio.plugin.system.GradleSystemListener
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File
import java.io.FileInputStream
import java.util.*

class GradleProfilerStartupActivity : ProjectActivity {
    companion object {
        private val LOG = Logger.getInstance(GradleProfilerStartupActivity::class.java)
        const val PROFILER_PORT_PROPERTY: String = "gradle.profiler.port"
    }

    override suspend fun execute(project: Project) {
        LOG.info("Project opened")
        logModifiedRegistryEntries()
        if (System.getProperty(PROFILER_PORT_PROPERTY) == null) return

        configureGradleSettings(project)
        // Register system listener already here, so we can catch any failure for syncs that are automatically started
        val gradleSystemListener = GradleSystemListener()
        ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(gradleSystemListener, project)

        project.setTrusted(true)

        ApplicationManager.getApplication().executeOnPooledThread {
            val lastRequest = listenForSyncRequests(project, gradleSystemListener)
            if (lastRequest.type == IdeRequestType.EXIT_IDE) {
                IdeSystemHelper.exit(project)
            }
        }
    }

    private fun logModifiedRegistryEntries() {
        val studioPropertiesPath = System.getenv("STUDIO_PROPERTIES")
        if (studioPropertiesPath == null || !File(studioPropertiesPath).exists()) {
            return
        }

        val properties = Properties().apply { this.load(FileInputStream(studioPropertiesPath)) }
        val modifiedValues = Registry.getAll()
            .filter { value: RegistryValue -> properties.containsKey(value.key) }
            .map { obj: RegistryValue -> obj.toString() }
            .sorted()
        LOG.info("Modified registry entries: $modifiedValues")
    }

    private fun configureGradleSettings(project: Project) {
        val gradleSettings = GradleSettings.getInstance(project)
        gradleSettings.linkedProjectsSettings.forEach {
            configureLinkedProject(it)
        }
        gradleSettings.subscribe(object : DefaultGradleSettingsListener() {
            override fun onProjectsLinked(linkedProjectsSettings: Collection<GradleProjectSettings>) {
                linkedProjectsSettings.forEach { configureLinkedProject(it) }
            }
        }, gradleSettings)
    }

    private fun configureLinkedProject(settings: GradleProjectSettings) {
        // If we don't disable external annotations, the IDE will download some artifacts
        // to .m2 folder if some project has for example com.fasterxml.jackson.core:jackson-core as a dependency
        settings.isResolveExternalAnnotations = false
        // Set Gradle JVM to JAVA_HOME to avoid JDK resolution dialogs in headless mode
        if (settings.gradleJvm == null || settings.gradleJvm == "#USE_PROJECT_JDK") {
            settings.gradleJvm = "#JAVA_HOME"
            LOG.info("Set Gradle JVM to #JAVA_HOME for ${settings.externalProjectPath}")
        }
    }

    private fun listenForSyncRequests(project: Project, gradleStartupListener: GradleSystemListener): IdeRequest {
        val port = Integer.getInteger(PROFILER_PORT_PROPERTY)
        Client(port).use {
            return GradleProfilerClient(it).listenForSyncRequests(project, gradleStartupListener)
        }
    }
}

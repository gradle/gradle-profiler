package org.gradle.profiler.intellij.plugin

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
import org.gradle.profiler.intellij.plugin.client.GradleProfilerClient
import org.gradle.profiler.intellij.plugin.system.GradleSystemListener
import org.gradle.profiler.intellij.plugin.system.IdeSystemHelper
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
        if (System.getProperty(PROFILER_PORT_PROPERTY) != null) {
            // Prevent downloading external annotations (e.g. for jackson-core) which adds noise to sync
            disableDownloadOfExternalAnnotations(project)
            // Register system listener already here, so we can catch any failure for syncs that are automatically started
            val gradleSystemListener = GradleSystemListener().apply {
                ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(this)
            }
            project.setTrusted(true)

            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val lastRequest = listenForSyncRequests(project, gradleSystemListener)
                    if (lastRequest.type == IdeRequestType.EXIT_IDE) {
                        IdeSystemHelper.exit(project)
                    }
                } finally {
                    ExternalSystemProgressNotificationManager.getInstance()
                        .removeNotificationListener(gradleSystemListener)
                }
            }
        }
    }

    private fun logModifiedRegistryEntries() {
        // IDEA_PROPERTIES is the standard env var set by the IntelliJ launcher when an
        // idea.properties file is provided; STUDIO_PROPERTIES is Android Studio's equivalent.
        val studioPropertiesPath = System.getenv("IDEA_PROPERTIES") ?: System.getenv("STUDIO_PROPERTIES")
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

    private fun disableDownloadOfExternalAnnotations(project: Project) {
        val gradleSettings = GradleSettings.getInstance(project)
        gradleSettings.linkedProjectsSettings.forEach {
            it.isResolveExternalAnnotations = false
        }
        gradleSettings.subscribe(object : DefaultGradleSettingsListener() {
            override fun onProjectsLinked(linkedProjectsSettings: Collection<GradleProjectSettings>) {
                linkedProjectsSettings.forEach {
                    it.isResolveExternalAnnotations = false
                }
            }
        }, gradleSettings)
    }

    private fun listenForSyncRequests(project: Project, gradleStartupListener: GradleSystemListener): IdeRequest {
        val port = Integer.getInteger(PROFILER_PORT_PROPERTY)
        Client(port).use {
            return GradleProfilerClient(it).listenForSyncRequests(project, gradleStartupListener)
        }
    }
}

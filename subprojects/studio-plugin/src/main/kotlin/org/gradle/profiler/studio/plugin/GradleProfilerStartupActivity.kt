package org.gradle.profiler.studio.plugin

import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.intellij.ide.impl.setTrusted
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import org.gradle.profiler.client.protocol.Client
import org.gradle.profiler.client.protocol.messages.StudioRequest
import org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType
import org.gradle.profiler.studio.plugin.client.GradleProfilerClient
import org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper
import org.gradle.profiler.studio.plugin.system.GradleSystemListener
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.UncheckedIOException
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

class GradleProfilerStartupActivity : ProjectActivity {
    companion object {
        private val LOG = Logger.getInstance(GradleProfilerStartupActivity::class.java)
        const val PROFILER_PORT_PROPERTY: String = "gradle.profiler.port"
    }

    override suspend fun execute(project: Project) {
        LOG.info("Project opened")
        logModifiedRegistryEntries()
        if (System.getProperty(PROFILER_PORT_PROPERTY) != null) {
            // This solves the issue where Android Studio would run the Gradle sync automatically on the first import.
            // Unfortunately it seems we can't always detect it because it happens very late and due to that there might
            // a case where two simultaneous syncs would be run: one from automatic sync trigger and one from our trigger.
            // With this line we disable that automatic sync, but we still trigger our sync later in the code.
            GradleProjectInfo.getInstance(project).isSkipStartupActivity = true
            // If we don't disable external annotations, Android Studio will download some artifacts
            // to .m2 folder if some project has for example com.fasterxml.jackson.core:jackson-core as a dependency
            disableDownloadOfExternalAnnotations(project)
            // Register system listener already here, so we can catch any failure for syncs that are automatically started
            val gradleSystemListener = GradleSystemListener().apply {
                ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(this)
            }
            project.setTrusted(true)

            ApplicationManager.getApplication().executeOnPooledThread {
                val lastRequest = listenForSyncRequests(project, gradleSystemListener)
                if (lastRequest.type == StudioRequestType.EXIT_IDE) {
                    AndroidStudioSystemHelper.exit(project)
                }
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

    private fun listenForSyncRequests(project: Project, gradleStartupListener: GradleSystemListener): StudioRequest {
        val port = Integer.getInteger(PROFILER_PORT_PROPERTY)
        Client(port).use {
            return GradleProfilerClient(it).listenForSyncRequests(project, gradleStartupListener)
        }
    }
}

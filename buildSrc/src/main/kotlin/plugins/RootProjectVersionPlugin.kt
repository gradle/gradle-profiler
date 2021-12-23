package plugins

import buildReceiptName
import extensions.VersionInfo
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Properties

const val incomingBuildReceiptLocation = "incoming-distributions/$buildReceiptName"

open class RootProjectVersionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val versionInfo = target.extensions.create<VersionInfo>("versionInfo")
        val incomingBuildReceipt = target.file(incomingBuildReceiptLocation)
        val profilerVersion = if (incomingBuildReceipt.isFile) {
            val properties = Properties()
            Files.newInputStream(incomingBuildReceipt.toPath()).use {
                properties.load(it)
            }
            properties.getProperty("version")
        } else {
            target.providers.gradleProperty("profiler.version").get()
        }
        versionInfo.version.set(profilerVersion)
        val createdBuildReceipt = target.layout.buildDirectory.file(buildReceiptName)
        target.tasks.register("createBuildReceipt") {
            outputs.file(createdBuildReceipt).withPropertyName("buildReceipt")
            inputs.property("version", versionInfo.version)
            doLast {
                createdBuildReceipt.get().asFile.writeText("version=${versionInfo.version.get()}", StandardCharsets.UTF_8)
            }
        }

        target.gradle.taskGraph.whenReady {
            if (hasTask(":publishToSonatype") || hasTask(":releaseToSdkMan"))
            target.logger.lifecycle("##teamcity[buildStatus text='{build.status.text}, Published version {}']", versionInfo.version.get())
        }
    }
}

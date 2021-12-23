import extensions.VersionInfo
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Properties

val incomingBuildReceiptLocation = "incoming-distributions/$buildReceiptName"

val versionInfo = extensions.create<VersionInfo>("versionInfo")
val incomingBuildReceipt = file(incomingBuildReceiptLocation)
val profilerVersion = if (incomingBuildReceipt.isFile) {
    val properties = Properties()
    Files.newInputStream(incomingBuildReceipt.toPath()).use {
        properties.load(it)
    }
    properties.getProperty("version")
} else {
    providers.gradleProperty("profiler.version").get()
}
versionInfo.version.set(profilerVersion)
val createdBuildReceipt = layout.buildDirectory.file(buildReceiptName)
tasks.register("createBuildReceipt") {
    outputs.file(createdBuildReceipt).withPropertyName("buildReceipt")
    inputs.property("version", versionInfo.version)
    doLast {
        createdBuildReceipt.get().asFile.writeText("version=${versionInfo.version.get()}", StandardCharsets.UTF_8)
    }
}

gradle.taskGraph.whenReady {
    if (hasTask(":publishToSonatype") || hasTask(":releaseToSdkMan")) {
        logger.lifecycle(
            "##teamcity[buildStatus text='{build.status.text}, Published version {}']",
            versionInfo.version.get()
        )
    }
}

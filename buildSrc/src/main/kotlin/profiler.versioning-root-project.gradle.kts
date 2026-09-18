import java.nio.charset.StandardCharsets

val profilerVersion = profilerVersion()
val createdBuildReceipt = layout.buildDirectory.file(buildReceiptName)
tasks.register("createBuildReceipt") {
    outputs.file(createdBuildReceipt).withPropertyName("buildReceipt")
    inputs.property("version", profilerVersion)
    val buildReceipt = createdBuildReceipt
    val version = profilerVersion
    doLast {
        buildReceipt.get().asFile.writeText("version=${version.get()}", StandardCharsets.UTF_8)
    }
}

gradle.taskGraph.whenReady {
    if (hasTask(":publishToMavenCentral") || hasTask(":releaseToSdkMan")) {
        logger.lifecycle(
            "##teamcity[buildStatus text='{build.status.text}, Published version {}']",
            profilerVersion.get()
        )
    }
}

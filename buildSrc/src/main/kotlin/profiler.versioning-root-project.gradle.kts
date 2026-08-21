import java.nio.charset.StandardCharsets

val profilerVersion = resolveProfilerVersion()
val createdBuildReceipt = layout.buildDirectory.file(buildReceiptName)
tasks.register("createBuildReceipt") {
    outputs.file(createdBuildReceipt).withPropertyName("buildReceipt")
    inputs.property("version", profilerVersion)
    val buildReceipt = createdBuildReceipt
    doLast {
        buildReceipt.get().asFile.writeText("version=$profilerVersion", StandardCharsets.UTF_8)
    }
}

gradle.taskGraph.whenReady {
    if (hasTask(":publishToSonatype") || hasTask(":releaseToSdkMan")) {
        logger.lifecycle(
            "##teamcity[buildStatus text='{build.status.text}, Published version {}']",
            profilerVersion
        )
    }
}

import jetbrains.buildServer.configs.kotlin.Project

fun Project.configureGradleProfilerPublishProject() {
    description = "Publishes the Gradle Profiler to Sonatype, Maven Central, and SDKman"

    buildType(GradleProfilerPublishing)
    buildType(GradleProfilerPublishToSdkMan(GradleProfilerPublishing))
}

import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.Project

fun Project.configureGradleProfilerProject() {
    description = "Runs tests and integration tests of the Gradle Profiler (https://github.com/gradle/gradle-profiler)"

    val testBuilds = listOf(
        MacOSJava8,
        WindowsJava11,
        LinuxJava8,
        LinuxJava11
    )

    testBuilds.forEach(this::buildType)

    buildType(GradleProfilerTestTrigger(testBuilds))
    buildType(GradleProfilerPublishing)
    buildType(GradleProfilerPublishToSdkMan(GradleProfilerPublishing))

    params {
        password("gradleprofiler.sdkman.key", "credentialsJSON:28e1a1ff-4594-4972-824c-5c3cdcaefc05", display = ParameterDisplay.HIDDEN)
        password("gradleprofiler.sdkman.token", "credentialsJSON:a8c19c49-5e95-408c-bc5f-4f89c9c4c24c", display = ParameterDisplay.HIDDEN)
        param("env.GRADLE_ENTERPRISE_ACCESS_KEY", "%ge.gradle.org.access.key%")
    }
}

import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.Project

fun Project.configureGradleProfilerProject() {
    description = "Runs tests and integration tests of the Gradle Profiler (https://github.com/gradle/gradle-profiler)"

    val testBuilds = listOf(
        MacOSJava11,
        MacOSJava17,
        MacOSJava21,
        MacOSJava25,
        WindowsJava11,
        WindowsJava17,
        WindowsJava21,
        WindowsJava25,
        LinuxJava8,
        LinuxJava11,
        LinuxJava17,
        LinuxJava21,
        LinuxJava25,
    )

    testBuilds.forEach(this::buildType)

    buildType(GradleProfilerTestTrigger(testBuilds))
    buildType(GradleProfilerPublishing)
    buildType(GradleProfilerPublishToSdkMan(GradleProfilerPublishing))

    params {
        text("JdkProviderEnabled", "true")
        password("gradleprofiler.sdkman.key", "credentialsJSON:28e1a1ff-4594-4972-824c-5c3cdcaefc05", display = ParameterDisplay.HIDDEN)
        password("gradleprofiler.sdkman.token", "credentialsJSON:a8c19c49-5e95-408c-bc5f-4f89c9c4c24c", display = ParameterDisplay.HIDDEN)
        param("env.DEVELOCITY_ACCESS_KEY", "%ge.gradle.org.access.key%")
    }
}

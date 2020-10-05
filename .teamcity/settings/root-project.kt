import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.Project

fun Project.configureGradleProfilerProject() {
    description = "Runs tests and integration tests of the Gradle Profiler (https://github.com/gradle/gradle-profiler)"

    buildType(MacOSJava18)
    buildType(WindowsJava18)
    buildType(LinuxJava18)

    buildType(GradleProfilerPublishing)

    params {
        password("gradleprofiler.sdkman.key", "credentialsJSON:a76beba1-f1a3-44c6-9995-9808bf652c9e", display = ParameterDisplay.HIDDEN)
        password("gradleprofiler.sdkman.token", "credentialsJSON:518893d3-6327-427f-a63f-b12b94399315", display = ParameterDisplay.HIDDEN)
        param("env.GRADLE_ENTERPRISE_ACCESS_KEY", "%ge.gradle.org.access.key%")
    }
}

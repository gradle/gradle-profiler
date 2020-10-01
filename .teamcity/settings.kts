/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.project
import jetbrains.buildServer.configs.kotlin.v2019_2.version

version = "2020.1"

project {
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

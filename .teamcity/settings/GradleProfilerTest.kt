import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.parallelTests
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.ui.add

open class GradleProfilerTest(os: Os, javaVersion: JavaVersion, arch: Arch) : BuildType({
    gradleProfilerVcs()

    params {
        // Java home must always use Java11
        // since intellij-gradle-plugin is not compatible with Java8
        javaHome(os, arch, JavaVersion.OPENJDK_11)
        androidHome(os)
    }

    steps {
        gradle {
            tasks = "clean test"
            buildFile = ""
            gradleParams = "--tests BuildOperationInstrumentationGradleCrossVersionTest -s" +
                " --build-cache ${toolchainConfiguration(os, arch)}" +
                " -PtestJavaVersion=${javaVersion.majorVersion}" +
                " -PtestJavaVendor=${javaVersion.vendor}" +
                " -PautoDownloadAndRunInHeadless=true"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    features {
        parallelTests {
            numberOfBatches = if (os == Os.windows) {
                8 // balance so Windows, Linux and macOS run with similar wall clock time
            } else {
                4
            }
        }

        commitStatusPublisher {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "%github.bot-teamcity.token%"
                }
            }
        }
    }

    failureConditions {
        add {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "%unmaskedFakeCredentials%"
                failureMessage = "This build might be leaking credentials"
                reverse = false
                stopBuildOnFailure = true
            }
        }
    }

    agentRequirement(os, arch)
}) {
    constructor(os: Os, javaVersion: JavaVersion, arch: Arch, init: GradleProfilerTest.() -> Unit) : this(os, javaVersion, arch) {
        init()
    }
}

object LinuxJava8 : GradleProfilerTest(Os.linux, JavaVersion.ORACLE_JAVA_8, Arch.AMD64, {
    name = "Linux - Java 8"
})

object LinuxJava11 : GradleProfilerTest(Os.linux, JavaVersion.OPENJDK_11, Arch.AMD64, {
    name = "Linux - Java 11"
})

object MacOSJava11 : GradleProfilerTest(Os.macos, JavaVersion.OPENJDK_11, Arch.AARCH64, {
    name = "macOS - Java 11"
})

object WindowsJava11 : GradleProfilerTest(Os.windows, JavaVersion.OPENJDK_11, Arch.AMD64, {
    name = "Windows - Java 11"
})

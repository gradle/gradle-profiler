import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.v2019_2.ui.add

open class GradleProfilerTest(os: Os, javaVersion: JavaVersion, arch: Arch = Arch.AMD64) : BuildType({
    gradleProfilerVcs()

    params {
        // Java home must always use Java11
        // since intellij-gradle-plugin is not compatible with Java8
        javaHome(os, JavaVersion.OPENJDK_11)
        androidHome(os)
    }

    steps {
        gradle {
            tasks = "clean test"
            buildFile = ""
            gradleParams = "-s" +
                " --build-cache ${toolchainConfiguration(os)}" +
                " -PtestJavaVersion=${javaVersion.majorVersion}" +
                " -PtestJavaVendor=${javaVersion.vendor}" +
                " -PautoDownloadAndRunInHeadless=true"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    features {
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
    constructor(os: Os, javaVersion: JavaVersion, init: GradleProfilerTest.() -> Unit) : this(os, javaVersion) {
        init()
    }
}

object LinuxJava8 : GradleProfilerTest(Os.linux, JavaVersion.ORACLE_JAVA_8, {
    name = "Linux - Java 8"
})

object LinuxJava11 : GradleProfilerTest(Os.linux, JavaVersion.OPENJDK_11, {
    name = "Linux - Java 11"
})

object MacOSJava8 : GradleProfilerTest(Os.macos, JavaVersion.ORACLE_JAVA_8, {
    name = "MacOS - Java 8"
})

object WindowsJava11 : GradleProfilerTest(Os.windows, JavaVersion.OPENJDK_11, {
    name = "Windows - Java 11"
})

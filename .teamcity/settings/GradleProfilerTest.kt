import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

open class GradleProfilerTest(os: Os) : BuildType({
    gradleProfilerVcs()

    params {
        java8Home(os)
    }

    steps {
        gradle {
            tasks = "clean build"
            buildFile = ""
            gradleParams = "-s $useGradleInternalScansServer"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    triggers {
        vcs {
            branchFilter = """
                +:*
                -:pull/*
            """.trimIndent()
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

    agentRequirement(os)
}) {
    constructor(os: Os, init: GradleProfilerTest.() -> Unit) : this(os) {
        init()
    }
}

object LinuxJava18 : GradleProfilerTest(Os.linux, {
    name = "Linux - Java 1.8"
})

object MacOSJava18 : GradleProfilerTest(Os.macos, {
    name = "MacOS - Java 1.8"
})

object WindowsJava18 : GradleProfilerTest(Os.windows, {
    name = "Windows - Java 1.8"
})

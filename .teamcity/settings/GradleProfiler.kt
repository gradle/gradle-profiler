import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs

open class GradleProfilerBuild() : BuildType({
    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            tasks = "clean build"
            buildFile = ""
            gradleParams = "-s"
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
                    token = "credentialsJSON:7c8c64ad-776d-4469-8400-5618da5de337"
                }
            }
        }
    }
}) {
    constructor(init: GradleProfilerBuild.() -> Unit) : this() {
        init()
    }
}

object LinuxJava18 : GradleProfilerBuild({
    name = "Linux - Java 1.8"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

object MacOSJava18 : GradleProfilerBuild({
    name = "MacOS - Java 1.8"

    params {
        param("env.JAVA_HOME", "%macos.java8.oracle.64bit%")
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Mac OS X")
    }
})

object WindowsJava18 : GradleProfilerBuild({
    name = "Windows - Java 1.8"

    params {
        param("env.JAVA_HOME", "%windows.java8.oracle.64bit%")
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Windows")
    }
})

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle

object GradleProfilerSanityCheck : BuildType({
    name = "Sanity Check"

    gradleProfilerVcs()

    val os = Os.linux
    val arch = Arch.AMD64

    params {
        javaHome(os, arch, JavaVersion.OPENJDK_17)
    }

    steps {
        gradle {
            tasks = "clean sanityCheck"
            buildFile = ""
            gradleParams = "-s --build-cache ${toolchainConfiguration(os, arch)}"
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

    agentRequirement(os, arch)
})

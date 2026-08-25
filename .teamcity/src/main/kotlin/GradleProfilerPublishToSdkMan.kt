import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger

class GradleProfilerPublishToSdkMan(publishingBuild: GradleProfilerPublishing) : BuildType({
    name = "Gradle profiler Publish to SDKman"
    description = "Publish Gradle profiler version to SDKman"

    artifactRules = """
        build/reports/** => .teamcity/reports
    """.trimIndent()

    gradleProfilerVcs()
    val os = Os.linux
    val arch = Arch.AMD64

    triggers {
        finishBuildTrigger {
            buildType = publishingBuild.id.toString()
            successfulOnly = true
            branchFilter = "+:master"
        }
    }

    params {
        text("additional.gradle.parameters", "")

        param("env.ORG_GRADLE_PROJECT_sdkmanKey", "%gradleprofiler.sdkman.key%")
        param("env.ORG_GRADLE_PROJECT_sdkmanToken", "%gradleprofiler.sdkman.token%")
    }

    steps {
        gradle {
            tasks = "releaseToSdkMan %additional.gradle.parameters%"
            gradleParams = toolchainConfiguration(os, arch) + " -Dgradle.cache.remote.push=true"
            buildFile = ""
        }
    }

    agentRequirement(os, arch)

    dependencies {
        artifacts(publishingBuild) {
            cleanDestination = true
            artifactRules = "$buildReceipt => incoming-distributions/"
        }
    }
})

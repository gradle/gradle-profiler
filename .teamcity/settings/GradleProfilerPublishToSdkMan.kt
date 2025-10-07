import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import jetbrains.buildServer.configs.kotlin.triggers.retryBuild

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
        retryBuild {
            delaySeconds = 30 * 60 // Wait for half an hour for the artifact to appear on Maven Central
            attempts = 1
            retryWithTheSameRevisions = true
        }
    }

    params {
        // Java home must always use Java11
        // since intellij-gradle-plugin is not compatible with Java8
        javaHome(os, arch, JavaVersion.OPENJDK_11)
        text("additional.gradle.parameters", "")

        param("env.ORG_GRADLE_PROJECT_sdkmanKey", "%gradleprofiler.sdkman.key%")
        param("env.ORG_GRADLE_PROJECT_sdkmanToken", "%gradleprofiler.sdkman.token%")
        param("env.GRADLE_CACHE_REMOTE_USERNAME", "%gradle.cache.remote.username%")
        param("env.GRADLE_CACHE_REMOTE_PASSWORD", "%gradle.cache.remote.password%")
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

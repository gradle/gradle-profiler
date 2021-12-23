import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.finishBuildTrigger
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.retryBuild

class GradleProfilerPublishToSdkMan(publishingBuild: GradleProfilerPublishing) : BuildType({
    name = "Gradle profiler Publish to SDKman"
    description = "Publish Gradle profiler version to SDKman"

    artifactRules = """
        build/reports/** => .teamcity/reports
    """.trimIndent()

    gradleProfilerVcs()
    val os = Os.linux

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
        javaHome(os, JavaVersion.OPENJDK_11)
        text("additional.gradle.parameters", "")

        param("env.ORG_GRADLE_PROJECT_sdkmanKey", "%gradleprofiler.sdkman.key%")
        param("env.ORG_GRADLE_PROJECT_sdkmanToken", "%gradleprofiler.sdkman.token%")
        param("env.GRADLE_CACHE_REMOTE_USERNAME", "%gradle.cache.remote.username%")
        param("env.GRADLE_CACHE_REMOTE_PASSWORD", "%gradle.cache.remote.password%")
    }

    steps {
        gradle {
            tasks = "releaseToSdkMan %additional.gradle.parameters%"
            gradleParams = toolchainConfiguration(os) + " -Dgradle.cache.remote.push=true"
            buildFile = ""
        }
    }

    agentRequirement(Os.linux)

    dependencies {
        artifacts(publishingBuild) {
            cleanDestination = true
            artifactRules = "$buildReceipt => incoming-distributions/"
        }
    }
})

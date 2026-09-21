import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle

object GradleProfilerPublishing : BuildType({
    name = "Gradle profiler Publishing"
    description = "Publish Gradle profiler Gradle's Artifactory repository"

    artifactRules = """
        build/reports/** => .teamcity/reports
        build/$buildReceipt => $buildReceipt
    """.trimIndent()

    gradleProfilerVcs()
    val os = Os.linux
    val arch = Arch.AMD64

    params {
        text("additional.gradle.parameters", "")

        param("env.ORG_GRADLE_PROJECT_githubToken", "%github.bot-teamcity.token%")
        param("env.ORG_GRADLE_PROJECT_sdkmanKey", "%gradleprofiler.sdkman.key%")
        param("env.ORG_GRADLE_PROJECT_sdkmanToken", "%gradleprofiler.sdkman.token%")
        param("env.PGP_SIGNING_KEY_ID", "%pgpSigningKeyId%")
        param("env.PGP_SIGNING_KEY", "%pgpSigningKey%")
        param("env.PGP_SIGNING_KEY_PASSPHRASE", "%pgpSigningPassphrase%")
        param("env.ORG_GRADLE_PROJECT_mavenCentralUsername", "%mavenCentralStagingRepoUser%")
        param("env.ORG_GRADLE_PROJECT_mavenCentralPassword", "%mavenCentralStagingRepoPassword%")
    }

    steps {
        gradle {
            tasks =
                "clean createBuildReceipt publishToMavenCentral gitPushTag publishToGithubReleases %additional.gradle.parameters%"
            gradleParams = toolchainConfiguration(os, arch) + " -Dgradle.cache.remote.push=true"
            buildFile = ""
        }
    }

    agentRequirement(os, arch)
})

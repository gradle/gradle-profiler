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
        param("env.GRADLE_CACHE_REMOTE_USERNAME", "%gradle.cache.remote.username%")
        param("env.GRADLE_CACHE_REMOTE_PASSWORD", "%gradle.cache.remote.password%")
        param("env.PGP_SIGNING_KEY", "%pgpSigningKey%")
        param("env.PGP_SIGNING_KEY_PASSPHRASE", "%pgpSigningPassphrase%")
        param("env.ORG_GRADLE_PROJECT_sonatypeUsername", "%mavenCentralStagingRepoUser%")
        param("env.ORG_GRADLE_PROJECT_sonatypePassword", "%mavenCentralStagingRepoPassword%")
    }

    steps {
        gradle {
            // No CC since https://github.com/gradle-nexus/publish-plugin/issues/221, which is
            // waiting for https://github.com/gradle/gradle/issues/22779
            //
            // TEMPORARY: Maven Central publishing (publishToSonatype
            // closeAndReleaseSonatypeStagingRepository) is disabled while we are over the Maven
            // Central quota. Re-add those two tasks once the quota recovers so library consumers
            // (gradle/gradle) get new versions of the profiler-as-a-library again.
            tasks = "--no-configuration-cache clean createBuildReceipt gitPushTag publishToGithubReleases %additional.gradle.parameters%"
            gradleParams = toolchainConfiguration(os, arch) + " -Dgradle.cache.remote.push=true"
            buildFile = ""
        }
    }

    agentRequirement(os, arch)
})

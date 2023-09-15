import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle

object GradleProfilerPublishing : BuildType({
    name = "Gradle profiler Publishing"
    description = "Publish Gradle profiler Gradle's Artifactory repository"

    artifactRules = """
        build/reports/** => .teamcity/reports
        build/$buildReceipt => $buildReceipt
    """.trimIndent()

    gradleProfilerVcs()
    val os = Os.linux

    params {
        // Java home must always use Java11
        // since intellij-gradle-plugin is not compatible with Java8
        javaHome(os, JavaVersion.OPENJDK_11)
        password("pgpSigningKey", "credentialsJSON:20c56c10-3c97-4753-91c2-685ddf26700e", display = ParameterDisplay.HIDDEN)
        password("pgpSigningPassphrase", "credentialsJSON:d49291bd-101e-4165-a9a8-912ca457926b", display = ParameterDisplay.HIDDEN)
        password("mavenCentralStagingRepoUser", "credentialsJSON:ce6ff00a-dc06-4b9b-aa1f-7b01bea2eb2f", display = ParameterDisplay.HIDDEN)
        password("mavenCentralStagingRepoPassword", "credentialsJSON:f3c71885-0cec-49c9-adcf-d21536fcf1ca", display = ParameterDisplay.HIDDEN)
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
            tasks = "clean createBuildReceipt publishToSonatype closeAndReleaseSonatypeStagingRepository gitPushTag %additional.gradle.parameters%"
            gradleParams = toolchainConfiguration(os) + " -Dgradle.cache.remote.push=true"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
            buildFile = ""
        }
    }

    agentRequirement(Os.linux)
})

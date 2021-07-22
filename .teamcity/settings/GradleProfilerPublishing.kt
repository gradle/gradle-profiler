import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle

object GradleProfilerPublishing : BuildType({
    name = "Gradle profiler Publishing"
    description = "Publish Gradle profiler Gradle's Artifactory repository"

    artifactRules = "build/reports/** => .teamcity/reports"

    gradleProfilerVcs()

    params {
        javaHome(Os.linux, JavaVersion.ORACLE_JAVA_8)
        param("env.ORG_GRADLE_PROJECT_artifactoryUsername", "bot-build-tool")
        param("env.ORG_GRADLE_PROJECT_artifactoryPassword", "%artifactoryUserPassword%")
        param("env.ORG_GRADLE_PROJECT_githubToken", "%github.bot-teamcity.token%")
        param("env.ORG_GRADLE_PROJECT_sdkmanKey", "%gradleprofiler.sdkman.key%")
        param("env.ORG_GRADLE_PROJECT_sdkmanToken", "%gradleprofiler.sdkman.token%")
        param("env.GRADLE_CACHE_REMOTE_USERNAME", "%gradle.cache.remote.username%")
        param("env.GRADLE_CACHE_REMOTE_PASSWORD", "%gradle.cache.remote.password%")
    }

    steps {
        gradle {
            tasks = "clean publishAllPublicationsToGradleBuildInternalRepository gitPushTag releaseToSdkMan"
            gradleParams = toolchainConfiguration(Os.linux) + " -Dgradle.cache.remote.push=true"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
            buildFile = ""
        }
    }

    agentRequirement(Os.linux)
})

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle

object GradleProfilerPublishing : BuildType({
    name = "Gradle profiler Publishing"
    description = "Publish Gradle profiler Gradle's Artifactory repository"

    artifactRules = "build/reports/** => .teamcity/reports"

    gradleProfilerVcs()

    params {
        java8Home(Os.linux)
        text("ARTIFACTORY_USERNAME", "bot-build-tool", allowEmpty = true)
        password("ARTIFACTORY_PASSWORD", "credentialsJSON:d94612fb-3291-41f5-b043-e2b3994aeeb4", display = ParameterDisplay.HIDDEN)
    }

    steps {
        gradle {
            tasks = "clean publishAllPublicationsToGradleBuildInternalRepository gitPushTag releaseToSdkMan"
            gradleParams = "-PartifactoryUsername=%ARTIFACTORY_USERNAME% -PartifactoryPassword=%ARTIFACTORY_PASSWORD% -PgithubToken=%github.bot-teamcity.token% " +
                "-PsdkmanKey=%gradleprofiler.sdkman.key% -PsdkmanToken=%gradleprofiler.sdkman.token% " +
                "${buildCacheConfigurations()} ${toolchainConfiguration(Os.linux)}"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
            buildFile = ""
        }
    }

    agentRequirement(Os.linux)
})

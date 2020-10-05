import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.ParametrizedWithType

fun BuildType.agentRequirement(os: Os) {
    requirements {
        contains("teamcity.agent.jvm.os.name", os.requirementName)
    }
}

fun buildCacheConfigurations() = listOf("-Dgradle.cache.remote.push=true",
    "-Dgradle.cache.remote.username=%gradle.cache.remote.username%",
    "-Dgradle.cache.remote.password=%gradle.cache.remote.password%").joinToString(" ")

fun toolchainConfiguration(os: Os) = listOf("-Porg.gradle.java.installations.auto-detect=false",
    "-Porg.gradle.java.installations.auto-download=false",
    """"-Porg.gradle.java.installations.paths=%${os.name}.java8.oracle.64bit%,%${os.name}.java11.openjdk.64bit%"""").joinToString(" ")

fun ParametrizedWithType.java8Home(os: Os) {
    param("env.JAVA_HOME", "%${os.name}.java8.oracle.64bit%")
}

fun BuildType.gradleProfilerVcs() {
    vcs {
        root(DslContext.settingsRoot)
        checkoutMode = CheckoutMode.ON_AGENT
    }
}

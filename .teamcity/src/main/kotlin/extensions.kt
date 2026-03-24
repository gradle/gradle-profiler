import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.CheckoutMode
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.ParametrizedWithType

fun BuildType.agentRequirement(os: Os, arch: Arch) {
    requirements {
        contains("teamcity.agent.jvm.os.name", os.requirementName)
        contains("teamcity.agent.jvm.os.arch", arch.localName(os))
    }
}

fun toolchainConfiguration(os: Os, arch: Arch): String {
    return listOf(
        "-Dorg.gradle.java.installations.auto-detect=false",
        "-Dorg.gradle.java.installations.auto-download=false",
        "-Dorg.gradle.java.installations.fromEnv=JDK11,JDK17,JDK21,JDK25",
    ).joinToString(" ")
}

fun ParametrizedWithType.androidHome(os: Os) {
    val androidHome = when (os) {
        Os.linux, Os.macos -> "/opt/android/sdk"
        Os.windows -> """C:\Program Files\android\sdk"""
    }
    param("env.ANDROID_HOME", androidHome)
    param("env.ANDROID_SDK_ROOT", androidHome)
}

enum class JavaVersion(val majorVersion: String, val vendor: String) {
    OPENJDK_17("17", "openjdk"),
    OPENJDK_21("21", "openjdk"),
    OPENJDK_25("25", "openjdk"),
    ;
}

fun BuildType.gradleProfilerVcs() {
    vcs {
        root(DslContext.settingsRoot)
        checkoutMode = CheckoutMode.ON_AGENT
    }
}

const val buildReceipt = "build-receipt.properties"

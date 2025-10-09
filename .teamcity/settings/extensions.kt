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

fun javaInstallationsFor(os: Os, arch: Arch) = listOf(
    if (os == Os.macos && arch == Arch.AARCH64) JavaVersion.ZULU_JAVA_8 else JavaVersion.ORACLE_JAVA_8,
    JavaVersion.OPENJDK_11,
    JavaVersion.OPENJDK_17,
).map { it.javaHome(os, arch) }

fun toolchainConfiguration(os: Os, arch: Arch): String {
    return listOf(
        "-Porg.gradle.java.installations.auto-detect=false",
        "-Porg.gradle.java.installations.auto-download=false",
        "\"-Porg.gradle.java.installations.paths=${javaInstallationsFor(os, arch).joinToString(",")}\"",
        "-Dorg.gradle.java.installations.auto-detect=false",
        "-Dorg.gradle.java.installations.auto-download=false",
        "\"-Dorg.gradle.java.installations.paths=${javaInstallationsFor(os, arch).joinToString(",")}\"",
    ).joinToString(" ")
}

fun ParametrizedWithType.javaHome(os: Os, arch: Arch, javaVersion: JavaVersion) {
    param("env.JAVA_HOME", javaVersion.javaHome(os, arch))
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
    ORACLE_JAVA_8("8", "oracle"),
    ZULU_JAVA_8("8", "zulu"),
    OPENJDK_11("11", "openjdk"),
    OPENJDK_17("17", "openjdk"),
    ;

    fun javaHome(os: Os, arch: Arch) = "%${os.name}.java${majorVersion}.${vendor}.${arch.jdkName}%"
}

fun BuildType.gradleProfilerVcs() {
    vcs {
        root(DslContext.settingsRoot)
        checkoutMode = CheckoutMode.ON_AGENT
    }
}

const val buildReceipt = "build-receipt.properties"

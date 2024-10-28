import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.ParametrizedWithType

fun BuildType.agentRequirement(os: Os, arch: Arch = Arch.AMD64) {
    requirements {
        contains("teamcity.agent.jvm.os.name", os.requirementName)
        if (os == Os.macos) {
            contains("teamcity.agent.jvm.os.arch", arch.nameOnMac)
        } else {
            contains("teamcity.agent.jvm.os.arch", arch.nameOnLinuxWindows)
        }
    }
}

fun toolchainConfiguration(os: Os) = listOf(
    "-Porg.gradle.java.installations.auto-detect=false",
    "-Porg.gradle.java.installations.auto-download=false",
    """"-Porg.gradle.java.installations.paths=%${os.name}.java8.oracle.64bit%,%${os.name}.java11.openjdk.64bit%,%${os.name}.java17.openjdk.64bit%""""
).joinToString(" ")

fun ParametrizedWithType.javaHome(os: Os, javaVersion: JavaVersion) {
    param("env.JAVA_HOME", javaVersion.javaHome(os))
}

fun ParametrizedWithType.androidHome(os: Os) {
    val androidHome = when (os) {
        Os.linux, Os.macos -> "/opt/android/sdk"
        Os.windows -> """C:\Program Files\android\sdk"""
    }
    param("env.ANDROID_HOME", androidHome)
    param("env.ANDROID_SDK_ROOT", androidHome)
}

enum class JavaVersion(val majorVersion: String, val vendor: String, private val javaHomePostfix: String) {
    ORACLE_JAVA_8("8", "oracle", "java8.oracle.64bit"),
    OPENJDK_11("11", "openjdk", "java11.openjdk.64bit");

    fun javaHome(os: Os) = "%${os.name}.$javaHomePostfix%"
}

fun BuildType.gradleProfilerVcs() {
    vcs {
        root(DslContext.settingsRoot)
        checkoutMode = CheckoutMode.ON_AGENT
    }
}

const val buildReceipt = "build-receipt.properties"

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.ParametrizedWithType

fun BuildType.agentRequirement(os: Os) {
    requirements {
        contains("teamcity.agent.jvm.os.name", os.requirementName)
    }
}

fun ParametrizedWithType.gradleEnterpriseAccessKey() {
    param("env.GRADLE_ENTERPRISE_ACCESS_KEY", "%ge.gradle.org.access.key%")
}

fun ParametrizedWithType.java8Home(os: Os) {
    param("env.JAVA_HOME", "%${os.name}.java8.oracle.64bit%")
}

fun BuildType.gradleProfilerVcs() {
    vcs {
        root(DslContext.settingsRoot)
        checkoutMode = CheckoutMode.ON_SERVER
    }
}

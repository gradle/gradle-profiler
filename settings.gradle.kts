plugins {
    id("com.gradle.develocity").version("4.1")
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.10.3")
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.10.0")
}

checkIfCurrentJavaIsCompatible()

rootProject.name = "gradle-profiler"

include("chrome-trace")
include("build-operations")
include("heap-dump")
include("client-protocol")
include("instrumentation-support")
include("studio-agent")
include("studio-plugin")

rootProject.children.forEach {
    it.projectDir = rootDir.resolve("subprojects/${it.name}")
}

/**
 * Multiple modules, including Gradle Profiler require Java 17 toolchain
 */
fun checkIfCurrentJavaIsCompatible() {
    if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
        throw GradleException("This project should be run with Java 17 or later, but it was run with Java ${JavaVersion.current()}.")
    }
}

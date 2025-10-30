plugins {
    id("com.gradle.develocity").version("4.1")
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.10.3")
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.10.0")
}

checkIfCurrentJavaIsCompatible()

rootProject.name = "gradle-profiler"

include("build-operations")
include("chrome-trace")
include("client-protocol")
include("flamegraph")
include("heap-dump")
include("instrumentation-support")
include("studio-agent")
include("studio-plugin")

rootProject.children.forEach {
    it.projectDir = rootDir.resolve( "subprojects/${it.name}")
}

/**
 * Intellij-gradle-plugin requires Java 11.
 */
fun checkIfCurrentJavaIsCompatible() {
    if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_11)) {
        throw GradleException("This project should be run with Java 11 or later, but it was run with Java ${JavaVersion.current()}.")
    }
}

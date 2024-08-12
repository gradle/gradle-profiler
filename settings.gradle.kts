plugins {
    id("com.gradle.enterprise").version("3.16.2")
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin").version("0.7.5")
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
    it.projectDir = rootDir.resolve( "subprojects/${it.name}")
}

/**
 * Building gradle-profile requires JDK 17.
 */
fun checkIfCurrentJavaIsCompatible() {
    if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
        throw GradleException("This project should be build with JDK 17, but it was run with Java ${JavaVersion.current()}.")
    }
}

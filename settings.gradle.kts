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
include("build-action")
include("scenario-definition")

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

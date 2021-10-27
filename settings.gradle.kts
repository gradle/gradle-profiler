plugins {
    id("com.gradle.enterprise").version("3.3.4")
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin").version("0.7.2")
}

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

enableFeaturePreview("VERSION_CATALOGS")

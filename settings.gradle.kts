plugins {
    id("com.gradle.enterprise").version("3.3.4")
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin").version("0.5")
}

rootProject.name = "gradle-profiler"

include("chrome-trace")
include("build-operations")
include("client-protocol")
include("instrumentation-support")
include("studio-agent")

rootProject.children.forEach {
    it.projectDir = rootDir.resolve( "subprojects/${it.name}")
}

plugins {
    id("com.gradle.develocity").version("4.1")
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.10.3")
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.10.0")
}

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
include("tooling-action")
include("perfetto-trace")

rootProject.children.forEach {
    it.projectDir = rootDir.resolve( "subprojects/${it.name}")
}

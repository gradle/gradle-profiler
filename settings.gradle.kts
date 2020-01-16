plugins {
    id("com.gradle.enterprise").version("3.1.1")
}

rootProject.name = "gradle-profiler"

include("chrome-trace")
include("build-operations")
rootProject.children.forEach {
    it.projectDir = rootDir.resolve( "subprojects/${it.name}")
}

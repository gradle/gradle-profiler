rootProject.name = "gradle-profiler"

include("chrome-trace")
include("build-operations")
rootProject.children.forEach {
    it.projectDir = rootDir.resolve( "subprojects/${it.name}")
}

enableFeaturePreview("GRADLE_METADATA")

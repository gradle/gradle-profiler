plugins {
    id("profiler.embedded-library")
    id("profiler.publication")
}

description = "Code for build operations measuring shared between the profiler and the plugin"

dependencies {
    testImplementation(libs.bundles.testDependencies)
}

plugins {
    id("profiler.embedded-library")
    id("profiler.publication")
}

description = "Support code for profilers that record the build process, such as JFR and async-profiler"

dependencies {
    testImplementation(libs.bundles.testDependencies)
}

plugins {
    id("profiler.embedded-library")
    id("groovy")
    id("profiler.publication")
}

description = "A client protocol for Gradle profiler to profile IDE sync"

dependencies {
    testImplementation(libs.bundles.testDependencies)
}

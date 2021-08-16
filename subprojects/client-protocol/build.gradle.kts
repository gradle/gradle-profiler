plugins {
    id("profiler.embedded-library")
    id("groovy")
    id("profiler.publication")
}

description = "A client protocol for Gradle profiler to profile Android Studio sync"

dependencies {
    testImplementation(libs.bundles.testDependencies)
}

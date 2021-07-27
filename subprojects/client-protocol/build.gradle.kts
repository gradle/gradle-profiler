plugins {
    id("profiler.embedded-library")
    id("groovy")
    id("profiler.publication")
}

dependencies {
    testImplementation(libs.bundles.testDependencies)
}

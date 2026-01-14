plugins {
    id("profiler.embedded-library")
    id("groovy")
    id("profiler.publication")
}

description = "A client protocol for Gradle profiler to profile Android Studio sync"

tasks.compileJava {
    options.release = 8
}

dependencies {
    testImplementation(libs.bundles.testDependencies)
}

plugins {
    id("profiler.embedded-library")
    id("groovy")
    id("profiler.publication")
}

description = "A client protocol for Gradle profiler to profile Android Studio sync"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

dependencies {
    testImplementation(libs.bundles.testDependencies)
}

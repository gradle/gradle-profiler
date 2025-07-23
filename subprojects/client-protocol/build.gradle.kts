plugins {
    id("profiler.embedded-library")
    id("groovy")
    id("profiler.publication")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

description = "A client protocol for Gradle profiler to profile Android Studio sync"

dependencies {
    testImplementation(libs.bundles.testDependencies)
}

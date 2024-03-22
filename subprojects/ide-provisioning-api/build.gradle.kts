plugins {
    id("profiler.embedded-library")
    id("profiler.publication")
}

description = "Api for IDE provisioning capabilities for Gradle profiler"


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

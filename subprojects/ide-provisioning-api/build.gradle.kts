plugins {
    id("profiler.embedded-library")
}

description = "Api for IDE provisioning capabilities for Gradle profiler"


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

plugins {
    id("profiler.embedded-library")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    api(gradleApi())
}

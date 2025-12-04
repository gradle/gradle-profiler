plugins {
    id("profiler.embedded-library")
}

dependencies {
    api(gradleApi())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<AbstractCompile>().configureEach {
    targetCompatibility = "8"
    sourceCompatibility = "8"
}

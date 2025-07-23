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
    targetCompatibility = "17"
    sourceCompatibility = "17"
}

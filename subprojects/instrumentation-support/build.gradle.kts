plugins {
    id("profiler.embedded-library")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.toolingApi)
    implementation(project(":client-protocol"))
}

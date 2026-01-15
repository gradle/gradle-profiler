plugins {
    id("profiler.embedded-library")
}

dependencies {
    api(gradleApi())
}

// Use Java 11 toolchain to access both internal sun.* APIs (Java 8) and ProcessHandle (Java 9+)
// This module has two PID collector implementations selected at runtime based on JVM version
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

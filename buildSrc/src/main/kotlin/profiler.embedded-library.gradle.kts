// Project packaged into the Gradle profiler Jar for the further injection to Gradle build.
// Uses Java 8 for compatibility with older Gradle versions.
plugins {
    id("profiler.java-library")
    id("groovy")
}

// Using sourceCompatibility/targetCompatibility instead of options.release because:
// - :build-operations needs sun.management APIs and Java 9+ ProcessHandle (multi-version code)
// - :ide-agent uses --add-exports which is incompatible with --release flag
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Tests are not embedded into the profiler Jar, so they can target the Java 17 toolchain.
// Needed for test dependencies that require Java 17, e.g. JUnit Platform 6.
tasks.named<JavaCompile>("compileTestJava") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
tasks.named<GroovyCompile>("compileTestGroovy") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

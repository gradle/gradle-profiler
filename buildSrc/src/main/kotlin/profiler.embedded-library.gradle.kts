plugins {
    id("profiler.java-library")
    id("groovy")
}

description =
    "Project packaged into the Gradle profiler Jar for the further injection to Gradle build. Uses Java 8 for compatibility with older Gradle versions."

// Using sourceCompatibility/targetCompatibility instead of options.release because:
// - :build-operations needs sun.management APIs and Java 9+ ProcessHandle (multi-version code)
// - :studio-agent uses --add-exports which is incompatible with --release flag
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

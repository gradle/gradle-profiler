plugins {
    id("profiler.java-library")
    id("groovy")
}

description =
    "Project packaged into the Gradle profiler Jar for the further injection to Gradle build. Uses Java 11 for compatibility with older Gradle versions (< 7.3)"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

// These projects are packaged into the Gradle profiler Jar, so let's make them reproducible
tasks.withType<Jar>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    dirMode = Integer.parseInt("0755", 8)
    fileMode = Integer.parseInt("0644", 8)
}

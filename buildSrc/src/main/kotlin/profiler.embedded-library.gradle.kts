plugins {
    id("profiler.java-library")
    id("groovy")
}

description =
    "Project packaged into the Gradle profiler Jar for the further injection to Gradle build. Uses Java 8 for compatibility with older Gradle versions."

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// These projects are packaged into the Gradle profiler Jar, so let's make them reproducible
tasks.withType<Jar>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    dirMode = Integer.parseInt("0755", 8)
    fileMode = Integer.parseInt("0644", 8)
}

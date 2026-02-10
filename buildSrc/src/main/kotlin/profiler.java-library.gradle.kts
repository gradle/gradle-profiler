plugins {
    id("profiler.java-base")
    id("java-library")
}

repositories {
    maven {
        name = "Gradle public repository"
        url = uri("https://repo.gradle.org/gradle/libs-releases")
        content {
            includeModule("org.gradle", "gradle-tooling-api")
        }
    }
    mavenCentral()
}

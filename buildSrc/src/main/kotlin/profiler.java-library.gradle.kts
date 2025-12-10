import org.gradle.internal.os.OperatingSystem

plugins {
    id("profiler.allprojects")
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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks.withType<Test>().configureEach {
    // Add OS as inputs since tests on different OS may behave differently https://github.com/gradle/gradle-private/issues/2831
    // the version currently differs between our dev infrastructure, so we only track the name and the architecture
    inputs.property("operatingSystem", "${OperatingSystem.current().name} ${System.getProperty("os.arch")}")
    useJUnitPlatform()
}

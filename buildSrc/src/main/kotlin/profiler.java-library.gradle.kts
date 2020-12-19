import org.gradle.internal.os.OperatingSystem

plugins {
    id("java-library")
}

repositories {
    jcenter()
    maven {
        url = uri("https://repo.gradle.org/gradle/repo")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

project.extensions.create<Versions>("versions")

abstract class Versions {
    val toolingApi = "org.gradle:gradle-tooling-api:6.6.1"
    val groovy = "org.codehaus.groovy:groovy:2.4.7"
    val spock = "org.spockframework:spock-core:1.1-groovy-2.4"
}

tasks.withType<Test>().configureEach {
    // Add OS as inputs since tests on different OS may behave differently https://github.com/gradle/gradle-private/issues/2831
    // the version currently differs between our dev infrastructure, so we only track the name and the architecture
    inputs.property("operatingSystem", "${OperatingSystem.current().name} ${System.getProperty("os.arch")}")
}

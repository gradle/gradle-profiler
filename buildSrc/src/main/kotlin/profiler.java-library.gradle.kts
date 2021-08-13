import org.gradle.internal.os.OperatingSystem

plugins {
    id("java-library")
}

repositories {
    maven {
        name = "Gradle public repository"
        url = uri("https://repo.gradle.org/gradle/public")
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

project.extensions.create<Versions>("versions")

abstract class Versions {
    val toolingApi = "org.gradle:gradle-tooling-api:6.6.1"
    val groovy = "org.codehaus.groovy:groovy:3.0.8"
    val groovyXml = "org.codehaus.groovy:groovy-xml:3.0.8"
    val spock = "org.spockframework:spock-core:2.0-groovy-3.0"
    val spockJunit4 = "org.spockframework:spock-junit4:2.0-groovy-3.0"
}

tasks.withType<Test>().configureEach {
    // Add OS as inputs since tests on different OS may behave differently https://github.com/gradle/gradle-private/issues/2831
    // the version currently differs between our dev infrastructure, so we only track the name and the architecture
    inputs.property("operatingSystem", "${OperatingSystem.current().name} ${System.getProperty("os.arch")}")
    useJUnitPlatform()
}

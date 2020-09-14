plugins {
    id("java-library")
}

repositories {
    jcenter()
    maven {
        url = uri("https://repo.gradle.org/gradle/repo")
    }
}

project.extensions.create("versions", Versions::class.java)

abstract class Versions {
    val toolingApi = "org.gradle:gradle-tooling-api:6.6.1"
    val groovy = "org.codehaus.groovy:groovy:2.4.7"
    val spock = "org.spockframework:spock-core:1.1-groovy-2.4"
}

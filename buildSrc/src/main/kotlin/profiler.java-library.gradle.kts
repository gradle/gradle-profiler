plugins {
    id("java-library")
}

project.extensions.create("versions", Versions::class.java)

abstract class Versions {
    val toolingApi = "org.gradle:gradle-tooling-api:6.6.1"
}

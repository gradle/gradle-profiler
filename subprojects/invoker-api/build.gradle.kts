plugins {
    id("profiler.java-library")
    id("groovy")
}

description = "APIs required to invoke different build tools"

dependencies {
    api(project(":profiler-api"))
    implementation("com.typesafe:config:1.3.3")
    implementation("commons-io:commons-io:2.6")
    implementation("com.github.javaparser:javaparser-core:3.18.0")
    implementation("com.google.guava:guava:27.1-android") {
        because("Gradle uses the android variant as well and we are running the same code there.")
    }
}

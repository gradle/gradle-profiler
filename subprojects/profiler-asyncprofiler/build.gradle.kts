plugins {
    id("profiler.java-library")
    id("groovy")
}

description = "Implementation of async profiler integration"

dependencies {
    api(project(":profiler-api"))
    implementation("com.google.guava:guava:27.1-android") {
        because("Gradle uses the android variant as well and we are running the same code there.")
    }
    implementation("org.apache.ant:ant-compress:1.5")
}

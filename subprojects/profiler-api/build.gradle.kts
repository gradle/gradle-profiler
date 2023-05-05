plugins {
    id("profiler.java-library")
    id("groovy")
}

description = "Common interface and types that all profilers need access to"

dependencies {
    api("net.sf.jopt-simple:jopt-simple:5.0.4")
    api("org.openjdk.jmc:flightrecorder:8.0.1")
    implementation("com.google.code.findbugs:annotations:3.0.1")
    implementation("com.google.guava:guava:27.1-android") {
        because("Gradle uses the android variant as well and we are running the same code there.")
    }
}

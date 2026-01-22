import org.gradle.internal.os.OperatingSystem

plugins {
    id("profiler.allprojects")
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    // Add OS as inputs since tests on different OS may behave differently https://github.com/gradle/gradle-private/issues/2831
    // the version currently differs between our dev infrastructure, so we only track the name and the architecture
    inputs.property("operatingSystem", "${OperatingSystem.current().name} ${System.getProperty("os.arch")}")
    useJUnitPlatform()
}

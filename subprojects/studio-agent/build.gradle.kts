plugins {
    id("profiler.embedded-library")
}

dependencies {
    implementation(project(":client-protocol"))
}

tasks.jar {
    manifest {
        attributes("Premain-Class" to "org.gradle.profiler.studio.agent.Agent")
    }
}

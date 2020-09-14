plugins {
    id("profiler.embedded-library")
}

dependencies {
    implementation("org.ow2.asm:asm:8.0.1")
    implementation(project(":client-protocol"))
}

tasks.jar {
    manifest {
        attributes("Premain-Class" to "org.gradle.profiler.studio.agent.Agent")
    }
}

plugins {
    id("profiler.embedded-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation("org.ow2.asm:asm:8.0.1")
    implementation(project(":client-protocol"))
}

tasks.compileJava {
    options.compilerArgs.add("--add-exports")
    options.compilerArgs.add("java.base/jdk.internal.misc=ALL-UNNAMED")
}

tasks.jar {
    manifest {
        attributes("Premain-Class" to "org.gradle.profiler.studio.agent.Agent")
    }
}

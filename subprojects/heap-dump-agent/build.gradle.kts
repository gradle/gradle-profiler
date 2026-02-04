plugins {
    id("profiler.embedded-library")
}

dependencies {
    implementation("org.ow2.asm:asm:9.2")
}

tasks.compileJava {
    // Need to set target/source compatibility for Java 11+
    targetCompatibility = "11"
    sourceCompatibility = "11"
}

tasks.jar {
    // Create a fat JAR with all dependencies
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Premain-Class" to "org.gradle.profiler.lifecycle.agent.GradleLifecycleAgent")
    }
}

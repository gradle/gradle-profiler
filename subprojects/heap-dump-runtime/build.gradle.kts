plugins {
    id("profiler.embedded-library")
}

tasks.compileJava {
    // Need to set target/source compatibility for Java 11+
    targetCompatibility = "11"
    sourceCompatibility = "11"
}

tasks.jar {
    manifest {
        attributes("Premain-Class" to "org.gradle.profiler.heapdump.agent.HeapDumpAgent")
    }
}

plugins {
    id("profiler.embedded-library")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    implementation("org.ow2.asm:asm:9.2")
    implementation(project(":client-protocol"))
}

tasks.compileJava {
    // Need to set target/source compatibility, since `--add-exports` is not compatible with `--release`.
    targetCompatibility = "11"
    sourceCompatibility = "11"
    options.compilerArgs.add("--add-exports")
    options.compilerArgs.add("java.base/jdk.internal.misc=ALL-UNNAMED")
}

tasks.jar {
    manifest {
        attributes("Premain-Class" to "org.gradle.profiler.studio.agent.Agent")
    }
}

plugins {
    id("profiler.publication")
    id("profiler.kotlin-library")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    maven {
        url = uri("https://www.jetbrains.com/intellij-repository/releases")
    }

    maven {
        url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
}

dependencies {
    implementation(project(":build-action"))
    implementation(project(":scenario-definition"))
    implementation(project(":client-protocol"))

    implementation("com.jetbrains.intellij.tools:ide-starter-squashed:252.27397.103")
    // kodein is supposed way to alter ide-starter behavior
    implementation("org.kodein.di:kodein-di-jvm:7.21.1")

    // ide-driver deps
    implementation("com.jetbrains.intellij.tools:ide-starter-driver:252.27397.103")
    implementation("com.jetbrains.intellij.driver:driver-sdk:252.27397.103")
    implementation("com.jetbrains.intellij.driver:driver-client:252.27397.103")
    implementation("com.jetbrains.intellij.driver:driver-model:252.27397.103")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("net.sf.jopt-simple:jopt-simple:5.0.4")

    // statistics collecting
    implementation("com.jetbrains.intellij.tools:ide-metrics-collector:252.27397.103")
    implementation("com.jetbrains.intellij.tools:ide-metrics-collector-starter:252.27397.103")
    implementation("com.jetbrains.fus.reporting:model:76")
}

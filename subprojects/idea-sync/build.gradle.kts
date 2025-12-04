plugins {
    id("profiler.embedded-library")
    kotlin("jvm") version "2.1.20"
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
    implementation(project(":core"))
    implementation(project(":client-protocol"))
    implementation("com.jetbrains.intellij.tools:ide-starter-squashed:252.27397.103")
    implementation("com.jetbrains.intellij.tools:ide-starter-driver:252.27397.103")
    implementation("com.jetbrains.intellij.driver:driver-sdk:252.27397.103")
    implementation("com.jetbrains.intellij.driver:driver-client:252.27397.103")
    implementation("com.jetbrains.intellij.driver:driver-model:252.27397.103")
    implementation("org.kodein.di:kodein-di-jvm:7.21.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("com.typesafe:config:1.3.3")
    implementation("net.sf.jopt-simple:jopt-simple:5.0.4")

    // statistics collecting
    implementation("com.jetbrains.intellij.tools:ide-metrics-collector:252.27397.103")
    implementation("com.jetbrains.intellij.tools:ide-metrics-collector-starter:252.27397.103")
    implementation("com.jetbrains.fus.reporting:model:76")
}

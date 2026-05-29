import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

kotlin {
    jvmToolchain(17)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone-243:0.27.0")
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window-243:0.27.0")

    implementation("org.jetbrains.exposed:exposed-core:0.56.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.56.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    implementation("com.typesafe:config:1.4.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

val profilerInstallDir = layout.buildDirectory.dir("appResources/gradle-profiler")

val syncProfiler by tasks.registering(Sync::class) {
    dependsOn(gradle.includedBuild("gradle-profiler").task(":installDist"))
    from(rootDir.parentFile.resolve("build/install/gradle-profiler"))
    into(profilerInstallDir)
}

compose.desktop {
    application {
        mainClass = "org.gradle.profiler.studio.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Gradle Performance Studio"
            packageVersion = "1.0.0"
            description = "UI for gradle-profiler"
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(syncProfiler)
}

tasks.matching { it.name == "run" }.configureEach {
    dependsOn(syncProfiler)
    (this as JavaExec).systemProperty(
        "studio.profilerHome",
        profilerInstallDir.get().asFile.absolutePath,
    )
}

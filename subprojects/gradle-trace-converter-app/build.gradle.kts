plugins {
    id("profiler.kotlin-base")
    id("groovy") // for testing
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":perfetto-trace"))

    testImplementation(libs.bundles.testDependencies)
}

application {
    mainClass = "org.gradle.tools.traceconverter.AppKt"
    applicationName = "gtc"
}

val installDistTask = tasks.named<Sync>("installDist")

tasks.register<Sync>("install") {
    val installDirName = "gtc.install.dir"
    val installDir = providers.gradleProperty(installDirName)
        .orElse(providers.systemProperty(installDirName))
        .map { file(it) }
        .orElse(layout.projectDirectory.dir("distribution").asFile)

    from(installDistTask.map { it.destinationDir })
    into(installDir)

    doLast {
        println("Installed gradle-trace-converter to '${installDir.get()}'")
    }
}

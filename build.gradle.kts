import com.moowork.gradle.node.npm.NpxTask
import io.sdkman.vendors.tasks.SdkAnnounceVersionTask
import io.sdkman.vendors.tasks.SdkDefaultVersionTask
import io.sdkman.vendors.tasks.SdkReleaseVersionTask
import io.sdkman.vendors.tasks.SdkmanVendorBaseTask
import java.net.URI

plugins {
    id("profiler.java-library")
    groovy
    application
    `maven-publish`
    id("profiler.publication")
    id("com.github.node-gradle.node") version "2.2.4"
    id("io.sdkman.vendors") version "2.0.0"
}

allprojects {
    group = "org.gradle.profiler"
    version = property("profiler.version") as String
}

val gradleRuntime by configurations.creating
val profilerPlugins by configurations.creating

dependencies {
    implementation(versions.toolingApi)
    implementation("com.google.code.findbugs:annotations:3.0.1")
    implementation("com.google.guava:guava:27.1-android") {
        because("Gradle uses the android variant as well and we are running the same code there.")
    }
    implementation("net.sf.jopt-simple:jopt-simple:5.0.4")
    implementation("com.typesafe:config:1.3.3")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("com.github.javaparser:javaparser-core:3.1.3")
    implementation("org.apache.ant:ant-compress:1.5")
    implementation("commons-io:commons-io:2.6")
    implementation("org.gradle.org.openjdk.jmc:flightrecorder:7.0.0-alpha01")
    implementation("com.googlecode.plist:dd-plist:1.23") {
        because("To extract launch details from Android Studio installation")
    }
    implementation("com.google.code.gson:gson:2.8.6") {
        because("To write JSON output")
    }
    implementation(project(":client-protocol"))

    gradleRuntime(gradleApi())
    gradleRuntime(versions.toolingApi)
    profilerPlugins(project(":chrome-trace"))
    profilerPlugins(project(":build-operations"))
    profilerPlugins(project(":instrumentation-support"))
    profilerPlugins(project(":studio-agent"))

    runtimeOnly("org.slf4j:slf4j-simple:1.7.10")
    testImplementation(versions.groovy)
    testImplementation(versions.spock)
    testRuntimeOnly("cglib:cglib:3.2.6")
    testRuntimeOnly("org.objenesis:objenesis:2.6")
}

subprojects {
    // Subprojects are packaged into the Gradle profiler Jar, so let's make them reproducible
    tasks.withType<Jar>().configureEach {
        isReproducibleFileOrder = true
        isPreserveFileTimestamps = false
        dirMode = Integer.parseInt("0755", 8)
        fileMode = Integer.parseInt("0644", 8)
    }
}

java {
    withSourcesJar()
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to "Gradle Profiler",
            "Implementation-Version" to project.version
        )
    }
}

application.mainClass.set("org.gradle.profiler.Main")

node {
    download = true
}

val generateHtmlReportJavaScript = tasks.register<NpxTask>("generateHtmlReportJavaScript") {
    val source = file("src/main/js/org/gradle/profiler/report/report.js")
    val outputDir = layout.buildDirectory.dir("html-report")
    val output = outputDir.map { it.file("org/gradle/profiler/report/report.js") }
    inputs.file(source)
    inputs.files(tasks.npmInstall)
    outputs.dir(outputDir)
    command = "browserify"
    setArgs(listOf(source.absolutePath, "--outfile", output.get().asFile))
}

tasks.processResources {
    into("META-INF/jars") {
        from(profilerPlugins.minus(gradleRuntime)) {
            // Removing the version from the JARs here, since they are referenced by name in production code.
            rename("""(.*)-\d\.\d.*\.jar""", "${'$'}1.jar")
        }
    }
    from(generateHtmlReportJavaScript)
}

tasks.test {
    // Use the current JVM. Some tests require JFR, which is only available in some JVM implementations
    // For now assume that the current JVM has JFR support and that CI will inject the correct implementation
    javaLauncher.set(null as JavaLauncher?)
}

val testReports = mapOf(
    "testHtmlReport" to "example",
    "testHtmlReportSingle" to "example-single",
    "testHtmlReportWithOps" to "example-with-build-operations",
    "testHtmlReportRegression" to "example-regression"
)
testReports.forEach { taskName, fileName ->
    tasks.register<ProcessResources>(taskName) {
        val dataFile = file("src/test/resources/org/gradle/profiler/report/${fileName}.json")
        inputs.file(dataFile)
        inputs.files(tasks.processResources)

        from("src/main/resources/org/gradle/profiler/report")
        into("$buildDir/test-html-report")
        rename("report-template.html", "test-report-${fileName}.html")
        filter { line ->
            if (line == "@@BENCHMARK_RESULT_JSON@@") dataFile.readText()
            else if (line == "@@SCRIPT@@") File(tasks.processResources.get().destinationDir, "org/gradle/profiler/report/report.js").readText(Charsets.UTF_8)
            else line
        }
    }
}

tasks.register("testHtmlReports") {
    dependsOn(testReports.keys)
}

val profilerDistribution = artifacts.add("archives", tasks.distZip.flatMap { it.archiveFile }) {
    type = "zip"
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            artifact(profilerDistribution)
        }
    }
}

fun Project.gradleInternalRepositoryUrl(): URI {
    val isSnapshot = version.toString().endsWith("-SNAPSHOT")
    val repositoryQualifier = if (isSnapshot) "snapshots" else "releases"
    return uri("https://repo.gradle.org/gradle/ext-$repositoryQualifier-local/")
}

buildScan {
    isCaptureTaskInputFiles = true
}

val releaseTagName = "v$version"

tasks.register<Exec>("gitTag") {
    commandLine("git", "tag", releaseTagName)
}

val gitPushTag = tasks.register<Exec>("gitPushTag") {
    mustRunAfter("publishAllPublicationsToGradleBuildInternalRepository")
    dependsOn("gitTag")
    commandLine("git", "push", "https://bot-teamcity:${project.property("githubToken")}@github.com/gradle/gradle-profiler.git", releaseTagName)
}

sdkman {
    api = "https://vendors.sdkman.io"
    candidate = "gradleprofiler"
    hashtag = "#gradleprofiler"
    version = project.version.toString()
    url = project.gradleInternalRepositoryUrl().resolve("org/gradle/profiler/gradle-profiler/$version/gradle-profiler-$version.zip").toString()
    consumerKey = project.findProperty("sdkmanKey") as String?
    consumerToken = project.findProperty("sdkmanToken") as String?
}

tasks.withType<SdkmanVendorBaseTask>().configureEach {
    mustRunAfter(gitPushTag)
}

tasks.withType<SdkDefaultVersionTask>().configureEach {
    mustRunAfter(tasks.withType<SdkReleaseVersionTask>())
}

tasks.withType<SdkAnnounceVersionTask>().configureEach {
    mustRunAfter(tasks.withType<SdkReleaseVersionTask>())
}

tasks.register("releaseToSdkMan") {
    val versionString = project.version.toString()
    // We only announce and set the default version for final releases
    // A release is not final if it contains things different to numbers and dots.
    // For example:
    //   - 1.3: final
    //   - 1.3.25: final
    //   - 1.3-rc-4: not final
    //   - 1.3.RC5: not final
    //   - 1.3-alpha5: not final
    val isFinalRelease = Regex("""[0-9\.]*""").matchEntire(versionString) != null
    dependsOn(tasks.withType<SdkReleaseVersionTask>())
    if (isFinalRelease) {
        dependsOn(tasks.withType<SdkDefaultVersionTask>())
        dependsOn(tasks.withType<SdkAnnounceVersionTask>())
    }
}

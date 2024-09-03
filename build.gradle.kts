import com.github.gradle.node.npm.task.NpxTask
import io.sdkman.vendors.tasks.SdkAnnounceVersionTask
import io.sdkman.vendors.tasks.SdkDefaultVersionTask
import io.sdkman.vendors.tasks.SdkReleaseVersionTask
import io.sdkman.vendors.tasks.SdkmanVendorBaseTask
import java.util.Locale

plugins {
    id("profiler.java-library")
    id("profiler.android-studio-setup")
    groovy
    application
    `maven-publish`
    id("profiler.publication")
    id("com.github.node-gradle.node") version "3.4.0"
    id("io.sdkman.vendors") version "2.0.0"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

description = "A tool to profile and benchmark Gradle builds"

val gradleRuntime by configurations.creating
val profilerPlugins by configurations.creating

dependencies {
    implementation(libs.toolingApi)
    implementation("com.google.code.findbugs:annotations:3.0.1")
    implementation("com.google.guava:guava:32.1.2-jre")
    implementation("com.typesafe:config:1.3.3")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("com.github.javaparser:javaparser-core:3.18.0")
    implementation("net.sf.jopt-simple:jopt-simple:5.0.4")
    implementation("org.apache.ant:ant-compress:1.5")
    implementation("org.apache.commons:commons-compress:1.27.1") {
        because("Avoid old version of commons-compress introduced by ant-compress")
    }
    implementation("commons-io:commons-io:2.16.1")
    implementation("org.openjdk.jmc:flightrecorder:8.0.1")
    implementation("com.googlecode.plist:dd-plist:1.23") {
        because("To extract launch details from Android Studio installation")
    }
    implementation("com.google.code.gson:gson:2.11.0") {
        because("To write JSON output")
    }
    implementation(project(":client-protocol"))

    gradleRuntime(gradleApi())
    gradleRuntime(libs.toolingApi)
    profilerPlugins(project(":chrome-trace"))
    profilerPlugins(project(":build-operations"))
    profilerPlugins(project(":instrumentation-support"))
    profilerPlugins(project(":studio-agent"))
    profilerPlugins(project(":heap-dump"))
    profilerPlugins(project(":studio-plugin"))

    runtimeOnly("org.slf4j:slf4j-simple:1.7.10")
    testImplementation(libs.bundles.testDependencies)
    testImplementation(libs.groovy.xml)
    testRuntimeOnly("cglib:cglib:3.2.6")
    testRuntimeOnly("org.objenesis:objenesis:2.6")
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
    download.set(true)
    version.set("17.6.0")
}

val generateHtmlReportJavaScript = tasks.register<NpxTask>("generateHtmlReportJavaScript") {
    dependsOn(tasks.npmInstall)
    val source = file("src/main/js/org/gradle/profiler/report/report.js")
    val outputDir = layout.buildDirectory.dir("html-report")
    val output = outputDir.map { it.file("org/gradle/profiler/report/report.js") }
    inputs.file(source)
    outputs.dir(outputDir)
    command.set("browserify")
    args.addAll(source.absolutePath, "--outfile", output.get().asFile.absolutePath)
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
    // If testJavaVersion is not set use the current JVM. Some tests require JFR, which is only available
    // in some JVM implementations. For now assume that the current JVM has JFR support.
    // CI will inject the correct implementation.
    val launcher = project.javaToolchains.launcherFor {
        val javaVersion = providers
            .gradleProperty("testJavaVersion")
            .getOrElse(JavaVersion.current().majorVersion)
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
        providers.gradleProperty("testJavaVendor").map {
            when (it.lowercase()) {
                "oracle" -> vendor.set(JvmVendorSpec.ORACLE)
                "openjdk" -> vendor.set(JvmVendorSpec.ADOPTIUM)
            }
        }.getOrNull()
    }
    javaLauncher.set(launcher)
    // So processes started from test also use same Java version
    environment("JAVA_HOME" to javaLauncher.get().metadata.installationPath)

    // We had some build failures on macOS, where it seems to be a Socket was already closed when trying to download the Gradle distribution.
    // The tests failing were consistenly in ProfilerIntegrationTest.
    // Running only ProfilerIntegrationTest did not expose the failures.
    // The problem went away when running every test class in its on JVM.
    // So I suppose the problem is that the JVM shares the TAPI client, and one of the tests leave the client in a bad state.
    // We now use forkEvery = 1 to run each test class in its own JVM, so we don't run into this problem any more.
    setForkEvery(1)
}

androidStudioTests {
    val autoDownloadAndRunInHeadless = providers.gradleProperty("autoDownloadAndRunInHeadless").orNull == "true"
    runAndroidStudioInHeadlessMode.set(autoDownloadAndRunInHeadless)
    autoDownloadAndroidStudio.set(autoDownloadAndRunInHeadless)
    testAndroidStudioVersion.set(libs.versions.testAndroidStudioVersion)
    testAndroidSdkVersion.set(libs.versions.testAndroidSdkVersion)
    // For local development it's easier to setup Android SDK with Android Studio, since auto download needs ANDROID_HOME or ANDROID_SDK_ROOT
    // to be set with an accepted license in it. See https://developer.android.com/studio/intro/update.html#download-with-gradle.
    autoDownloadAndroidSdk.set(autoDownloadAndRunInHeadless)
}

val testReports = mapOf(
    "testHtmlReport" to "example",
    "testHtmlReportSingle" to "example-single",
    "testHtmlReportCacheSize" to "example-cache-size",
    "testHtmlReportWithOps" to "example-with-build-operations",
    "testHtmlReportRegression" to "example-regression"
)
testReports.forEach { taskName, fileName ->
    tasks.register<ProcessResources>(taskName) {
        val dataFile = file("src/test/resources/org/gradle/profiler/report/${fileName}.json")
        inputs.file(dataFile)
        inputs.files(tasks.processResources)

        from("src/main/resources/org/gradle/profiler/report")
        into(layout.buildDirectory.dir("test-html-report"))
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
            pom {
                // For some reason adding the zip artifact changes the packaging to "pom"
                packaging = "jar"
            }
        }
    }
}

nexusPublishing {
    packageGroup.set(project.group.toString())
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}

buildScan {
    isCaptureTaskInputFiles = true
}

val releaseTagName = "v$version"

tasks.register<Exec>("gitTag") {
    commandLine("git", "tag", releaseTagName)
    onlyIf { !isSnapshot() }
}

val gitPushTag = tasks.register<Exec>("gitPushTag") {
    mustRunAfter("closeSonatypeStagingRepository")
    dependsOn("gitTag")
    onlyIf { !isSnapshot() }
    commandLine("git", "push", "https://bot-teamcity:${project.findProperty("githubToken")}@github.com/gradle/gradle-profiler.git", releaseTagName)
}

fun Project.isSnapshot() = version.toString().endsWith("-SNAPSHOT")

sdkman {
    api = "https://vendors.sdkman.io"
    candidate = "gradleprofiler"
    hashtag = "#gradleprofiler"
    version = project.version.toString()
    url = "https://repo1.maven.org/maven2/org/gradle/profiler/gradle-profiler/$version/gradle-profiler-$version.zip"
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

    // We don't publish snapshots and alphas at all to SDKman.
    val isSnapshotOrAlphaRelease = versionString.lowercase(Locale.US).run { contains("snapshot") || contains("alpha") }
    if (!isSnapshotOrAlphaRelease) {
        dependsOn(tasks.withType<SdkReleaseVersionTask>())

        // We only announce and set the default version for final releases
        // A release is not final if it contains things different to numbers and dots.
        // For example:
        //   - 1.3: final
        //   - 1.3.25: final
        //   - 1.3-rc-4: not final
        //   - 1.3.RC5: not final
        //   - 1.3-milestone5: not final
        val isFinalRelease = Regex("""[0-9\.]*""").matchEntire(versionString) != null
        if (isFinalRelease) {
            dependsOn(tasks.withType<SdkDefaultVersionTask>())
            dependsOn(tasks.withType<SdkAnnounceVersionTask>())
        }
    }
}

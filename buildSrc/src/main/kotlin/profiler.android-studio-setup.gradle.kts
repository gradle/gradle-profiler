import org.graalvm.compiler.hotspot.debug.BenchmarkCounters.enabled
import org.gradle.internal.classpath.Instrumented.systemProperty
import org.gradle.kotlin.dsl.creating

repositories {
    ivy {
        url = uri("https://redirector.gvt1.com/edgedl/android/studio/ide-zips")
        patternLayout {
            artifact("[revision]/[artifact]-[revision]-[ext]")
        }
        metadataSources { artifact() }
    }
}

val os = System.getProperty("os.name").toLowerCase()
val architecture = System.getProperty("os.arch").toLowerCase()
fun isWindows(): Boolean = os.startsWith("windows")
fun isMacOS(): Boolean = os.startsWith("mac")
fun isLinux(): Boolean = os.startsWith("linux")
fun isIntel(): Boolean = architecture == "x86_64" || architecture == "x86"

val androidStudioRuntime by configurations.creating
dependencies {
    when {
        isWindows() -> androidStudioRuntime("android-studio:android-studio:2021.1.1.16@windows.zip")
        isMacOS() && isIntel() -> androidStudioRuntime("android-studio:android-studio:2021.1.1.16@mac.zip")
        isMacOS() && !isIntel() -> androidStudioRuntime("android-studio:android-studio:2021.1.1.16@mac_arm.zip")
        isLinux() -> androidStudioRuntime("android-studio:android-studio:2021.1.1.16@linux.tar.gz")
    }
}

val androidStudioPath by lazy { "$buildDir/android-studio" }
val unpackAndroidStudio = tasks.register<Copy>("unpackAndroidStudio") {
    if (androidStudioRuntime.isEmpty) {
        enabled = false
        return@register
    }
    val file = androidStudioRuntime.files.first()
    inputs.file(file)
    outputs.dir(androidStudioPath)
    val fileTree = when {
        file.name.endsWith("tar.gz") -> tarTree(file)
        else -> zipTree(file)
    }
    from(fileTree)
    into(androidStudioPath)
}

tasks.withType<Test>().configureEach {
    dependsOn(unpackAndroidStudio)
    val subfolder = when {
        isMacOS() -> if (file("$androidStudioPath/Android Studio.app").exists()) { "Android Studio.app" } else { "Android Studio Preview.app" }
        else -> "android-studio"
    }
    systemProperty("studio.home", "$androidStudioPath/$subfolder")
    if (providers.gradleProperty("runStudioTestsHeadless").orNull == "true") {
        systemProperty("studio.tests.headless", "true")
    }
}

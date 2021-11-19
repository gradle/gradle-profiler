

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
//        androidStudioRuntime("android-studio:android-studio:2021.1.1.14@windows.zip")
      androidStudioRuntime("android-studio:android-studio:2021.1.1.14@linux.tar.gz")
//    when {
//        isMacOS() && isIntel() -> androidStudioRuntime("android-studio:android-studio:2021.1.1.14@mac.zip")
//        isMacOS() && !isIntel() -> androidStudioRuntime("android-studio:android-studio:2021.1.1.14@mac_arm.zip")
//    }
}

val androidStudioPath by lazy { "$buildDir/android-studio" }
val unpackAndroidStudio = tasks.register<Copy>("unpackAndroidStudio") {
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
    systemProperty("studio.tests.headless", "true")
//    if (providers.gradleProperty("runStudioTestsHeadless").get() == "true") {
//    }
}

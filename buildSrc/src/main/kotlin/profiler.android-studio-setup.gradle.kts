import extensions.AndroidStudioTestExtension

repositories {
    ivy {
        // Url of Android Studio archive
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
fun File.isTar(): Boolean = name.endsWith(".tar.gz")

val extension = extensions.create<AndroidStudioTestExtension>("androidStudioTests")

val androidStudioRuntime by configurations.creating
val autoDownloadAndroidStudio by lazy { extension.autoDownloadAndroidStudio.getOrElse(false) }
afterEvaluate {
    if (autoDownloadAndroidStudio) {
        dependencies {
            val androidStudioVersion = extension.testAndroidStudioVersion.get()
            when {
                isWindows() -> androidStudioRuntime("android-studio:android-studio:$androidStudioVersion@windows.zip")
                isMacOS() && isIntel() -> androidStudioRuntime("android-studio:android-studio:$androidStudioVersion@mac.zip")
                isMacOS() && !isIntel() -> androidStudioRuntime("android-studio:android-studio:$androidStudioVersion@mac_arm.zip")
                isLinux() -> androidStudioRuntime("android-studio:android-studio:$androidStudioVersion@linux.tar.gz")
            }
        }
    }
}

val androidStudioPath = "$buildDir/android-studio"
val unpackAndroidStudio = tasks.register<Copy>("unpackAndroidStudio") {
    if (autoDownloadAndroidStudio) {
        val file = androidStudioRuntime.files.first()
        inputs.file(file)
        outputs.dir(androidStudioPath)
        val fileTree = when {
            file.isTar() -> tarTree(file)
            else -> zipTree(file)
        }
        from(fileTree)
        into(androidStudioPath)
    }
}

val macOsAndroidStudioPath = "$androidStudioPath/Android Studio.app"
val macOsAndroidStudioPathPreview = "$androidStudioPath/Android Studio Preview.app"
val windowsAndLinuxPath = "$androidStudioPath/android-studio"
tasks.withType<Test>().configureEach {
    dependsOn(unpackAndroidStudio)
    if (autoDownloadAndroidStudio) {
        val studioHome = when {
            isMacOS() && file(macOsAndroidStudioPath).exists() -> macOsAndroidStudioPath
            isMacOS() -> macOsAndroidStudioPathPreview
            else -> windowsAndLinuxPath
        }
        systemProperty("studio.home", studioHome)
    }
    if (extension.runAndroidStudioInHeadlessMode.getOrElse(false)) {
        systemProperty("studio.tests.headless", "true")
    }
}

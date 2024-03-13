import extensions.AndroidStudioTestExtension
import tasks.InstallAndroidSdkTask
import providers.AndroidStudioInstallation
import providers.AndroidStudioSystemProperties

repositories {
    ivy {
        // Url of Android Studio archive
        url = uri("https://redirector.gvt1.com/edgedl/android/studio/ide-zips")
        patternLayout {
            artifact("[revision]/[artifact]-[revision]-[ext]")
        }
        metadataSources { artifact() }
        content {
            includeGroup("android-studio")
        }
    }
}

val os = System.getProperty("os.name").lowercase()
val architecture = System.getProperty("os.arch").lowercase()
fun isWindows(): Boolean = os.startsWith("windows")
fun isMacOS(): Boolean = os.startsWith("mac")
fun isLinux(): Boolean = os.startsWith("linux")
fun isIntel(): Boolean = architecture == "x86_64" || architecture == "x86"

val extension = extensions.create<AndroidStudioTestExtension>("androidStudioTests").apply {
    autoDownloadAndroidStudio.convention(false)
    runAndroidStudioInHeadlessMode.convention(false)
    autoDownloadAndroidSdk.convention(false)
}

val androidStudioRuntime by configurations.creating

dependencies {
    val fileExtension = when {
        isWindows() -> "windows-exe.zip"
        isMacOS() && isIntel() -> "mac.zip"
        isMacOS() && !isIntel() -> "mac_arm.zip"
        isLinux() -> "linux.tar.gz"
        else -> throw IllegalStateException("Unsupported OS: $os")
    }
    androidStudioRuntime(extension.testAndroidStudioVersion.map { version -> "android-studio:android-studio:$version@$fileExtension" })
}

val unpackAndroidStudio = tasks.register<Copy>("unpackAndroidStudio") {
    from(Callable {
        val singleFile = androidStudioRuntime.singleFile
        when {
            singleFile.name.endsWith(".tar.gz") -> tarTree(singleFile)
            else -> zipTree(singleFile)
        }
    }) {
        eachFile {
            // Remove top folder when unzipping, that way we get rid of Android Studio.app folder that can cause issues on Mac
            // where MacOS would kill the Android Studio process right after start, issue: https://github.com/gradle/gradle-profiler/issues/469
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
    }
    into("$buildDir/android-studio")
}

val installAndroidSdk = tasks.register<InstallAndroidSdkTask>("installAndroidSdk") {
    androidSdkVersion.set(extension.testAndroidSdkVersion)
    androidProjectDir.set(layout.buildDirectory.dir("installAndroidSdk/android-sdk-project"))
    val autoDownloadAndroidSdk = extension.autoDownloadAndroidSdk
    onlyIf { autoDownloadAndroidSdk.get() }
}

val androidStudioInstallation = objects.newInstance<AndroidStudioInstallation>().apply {
    studioInstallLocation.fileProvider(unpackAndroidStudio.map { it.destinationDir })
}

tasks.withType<Test>().configureEach {
    dependsOn(installAndroidSdk)
    jvmArgumentProviders.add(
        AndroidStudioSystemProperties(
            androidStudioInstallation,
            extension.autoDownloadAndroidStudio,
            extension.runAndroidStudioInHeadlessMode,
            providers
        )
    )
}

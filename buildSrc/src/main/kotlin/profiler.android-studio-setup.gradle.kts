import extensions.AndroidStudioTestExtension
import tasks.InstallAndroidSdkTask
import providers.AndroidStudioInstallation
import providers.AndroidStudioSystemProperties
import tasks.ExtractAndroidStudioTask

repositories {
    listOf(
        // Urls of Android Studio archive
        "https://redirector.gvt1.com/edgedl/android/studio/ide-zips",
        "https://redirector.gvt1.com/edgedl/android/studio/install"
    ).forEach {
        ivy {
            url = uri(it)
            patternLayout {
                artifact("[revision]/[artifact]-[revision]-[ext]")
            }
            metadataSources { artifact() }
            content {
                includeGroup("android-studio")
            }
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
        isWindows() -> "windows.zip"
        isMacOS() && isIntel() -> "mac.dmg"
        isMacOS() && !isIntel() -> "mac_arm.dmg"
        isLinux() -> "linux.tar.gz"
        else -> throw IllegalStateException("Unsupported OS: $os")
    }
    androidStudioRuntime(extension.testAndroidStudioVersion.map { version -> "android-studio:android-studio:$version@$fileExtension" })
}

val unpackAndroidStudio = tasks.register<ExtractAndroidStudioTask>("unpackAndroidStudio") {
    androidStudioLocation = androidStudioRuntime
    outputDir = layout.buildDirectory.dir("android-studio")
}

val installAndroidSdk = tasks.register<InstallAndroidSdkTask>("installAndroidSdk") {
    androidSdkVersion.set(extension.testAndroidSdkVersion)
    androidProjectDir.set(layout.buildDirectory.dir("installAndroidSdk/android-sdk-project"))
    val autoDownloadAndroidSdk = extension.autoDownloadAndroidSdk
    onlyIf { autoDownloadAndroidSdk.get() }
}

val androidStudioInstallation = objects.newInstance<AndroidStudioInstallation>().apply {
    studioInstallLocation = unpackAndroidStudio.flatMap { it.outputDir }
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

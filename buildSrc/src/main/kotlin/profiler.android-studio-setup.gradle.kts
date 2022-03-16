import extensions.AndroidStudioTestExtension
import tasks.InstallAndroidSdkTask

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

val os = System.getProperty("os.name").toLowerCase()
val architecture = System.getProperty("os.arch").toLowerCase()
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
    })
    into("$buildDir/android-studio")
}

val installAndroidSdk = tasks.register<InstallAndroidSdkTask>("installAndroidSdk") {
    androidSdkVersion.set(extension.testAndroidSdkVersion)
    androidProjectDir.set(layout.buildDirectory.dir("installAndroidSdk/android-sdk-project"))
    onlyIf { extension.autoDownloadAndroidSdk.get() }
}

val androidStudioInstallation = objects.newInstance<AndroidStudioInstallation>().apply {
    studioInstallLocation.fileProvider(unpackAndroidStudio.map { it.destinationDir })
}

tasks.withType<Test>().configureEach {
    dependsOn(installAndroidSdk)
    jvmArgumentProviders.add(AndroidStudioSystemProperties(
        androidStudioInstallation,
        extension.autoDownloadAndroidStudio,
        extension.runAndroidStudioInHeadlessMode,
        providers
    ))
}

abstract class AndroidStudioInstallation {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val studioInstallLocation: DirectoryProperty
}

class AndroidStudioSystemProperties(
    @get:Internal
    val studioInstallation: AndroidStudioInstallation,
    @get:Internal
    val autoDownloadAndroidStudio: Provider<Boolean>,
    @get:Input
    val runInHeadlessMode: Provider<Boolean>,
    providers: ProviderFactory
) : CommandLineArgumentProvider {

    @get:Optional
    @get:Nested
    val studioInstallationProvider = providers.provider {
        if (autoDownloadAndroidStudio.get()) {
            studioInstallation
        } else {
            null
        }
    }

    override fun asArguments(): Iterable<String> {
        val systemProperties = mutableListOf<String>()
        if (autoDownloadAndroidStudio.get()) {
            val androidStudioPath = studioInstallation.studioInstallLocation.get().asFile.absolutePath
            val macOsAndroidStudioPath = "$androidStudioPath/Android Studio.app"
            val macOsAndroidStudioPathPreview = "$androidStudioPath/Android Studio Preview.app"
            val windowsAndLinuxPath = "$androidStudioPath/android-studio"
            val studioHome = when {
                isMacOS() && File(macOsAndroidStudioPath).exists() -> macOsAndroidStudioPath
                isMacOS() -> macOsAndroidStudioPathPreview
                else -> windowsAndLinuxPath
            }
            systemProperties.add("-Dstudio.home=$studioHome")
        }
        if (runInHeadlessMode.get()) {
            systemProperties.add("-Dstudio.tests.headless=true")
        }
        return systemProperties
    }
}

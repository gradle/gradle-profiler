import extensions.AndroidStudioTestExtension

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
}

val androidStudioRuntime by configurations.creating

dependencies {
    val extension = when {
        isWindows() -> "windows.zip"
        isMacOS() && isIntel() -> "mac.zip"
        isMacOS() && !isIntel() -> "mac_arm.zip"
        isLinux() -> "linux.tar.gz"
        else -> throw IllegalStateException("Unsupported OS: $os")
    }
    androidStudioRuntime("android-studio:android-studio@$extension")
}
androidStudioRuntime.withDependencies {
    this.forEach { dependency ->
        if (dependency is ExternalDependency && dependency.version == null) {
            dependency.version { require(extension.testAndroidStudioVersion.get()) }
        }
    }
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

val androidStudioInstallation = objects.newInstance<AndroidStudioInstallation>().apply {
    studioInstallLocation.fileProvider(unpackAndroidStudio.map { it.destinationDir })
}

tasks.withType<Test>().configureEach {
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
    val installationProvider = providers.provider {
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

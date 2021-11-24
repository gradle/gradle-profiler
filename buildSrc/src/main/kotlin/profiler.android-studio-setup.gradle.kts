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

val extension = extensions.create<AndroidStudioTestExtension>("androidStudioTests")

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

val androidStudioPath = "$buildDir/android-studio"
val unpackAndroidStudio = tasks.register<Copy>("unpackAndroidStudio") {
    from(Callable {
        val singleFile = androidStudioRuntime.singleFile
        when {
            singleFile.name.endsWith(".tar.gz") -> tarTree(singleFile)
            else -> zipTree(singleFile)
        }
    })
    into(androidStudioPath)
}


val androidStudioInstallation = objects.newInstance<AndroidStudioInstallation>()
androidStudioInstallation.studioInstallLocation.fileProvider(unpackAndroidStudio.map { it.destinationDir })
androidStudioInstallation.installation.set(providers.provider {
    if (extension.autoDownloadAndroidStudio.getOrElse(false)) {
        AndroidStudioInstallation.InputDeclaration(
            androidStudioInstallation.studioInstallLocation,
            extension.testAndroidStudioVersion
        )
    } else {
        null
    }
})

tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(androidStudioInstallation)
    if (extension.runAndroidStudioInHeadlessMode.getOrElse(false)) {
        systemProperty("studio.tests.headless", "true")
    }
}

abstract class AndroidStudioInstallation : CommandLineArgumentProvider {
    @get:Internal
    abstract val studioInstallLocation: DirectoryProperty

    class InputDeclaration(
        @get:Internal
        val studioInstallLocation: DirectoryProperty,
        @get:Internal
        val studioVersion: Property<String>
        ) {
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val studioLocationOnly: FileCollection
            get() = studioInstallLocation.asFileTree.matching {
                exclude("**/*")
            }
    }

    @get:Optional
    @get:Nested
    abstract val installation: Property<InputDeclaration>

    @get:Optional
    @get:Input
    abstract val autoDownloadAndroidStudio: Property<Boolean>

    override fun asArguments(): Iterable<String> {
        val os = System.getProperty("os.name").toLowerCase()
        val isMacOS = os.startsWith("mac")
        val androidStudioPath = studioInstallLocation.get().asFile.absolutePath
        val macOsAndroidStudioPath = "$androidStudioPath/Android Studio.app"
        val macOsAndroidStudioPathPreview = "$androidStudioPath/Android Studio Preview.app"
        val windowsAndLinuxPath = "$androidStudioPath/android-studio"
        val studioHome = when {
            isMacOS && File(macOsAndroidStudioPath).exists() -> macOsAndroidStudioPath
            isMacOS -> macOsAndroidStudioPathPreview
            else -> windowsAndLinuxPath
        }
        return listOf("-Dstudio.home=$studioHome")
    }
}

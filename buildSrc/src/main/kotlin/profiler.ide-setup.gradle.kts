import extensions.AndroidStudioTestExtension
import extensions.IntellijTestExtension
import providers.IdeInstallation
import providers.IdeSystemProperties
import tasks.ExtractIdeTask
import tasks.InstallAndroidSdkTask

val os = System.getProperty("os.name").lowercase()
val architecture = System.getProperty("os.arch").lowercase()
fun isWindows(): Boolean = os.startsWith("windows")
fun isMacOS(): Boolean = os.startsWith("mac")
fun isLinux(): Boolean = os.startsWith("linux")
fun isIntel(): Boolean = architecture == "x86_64" || architecture == "x86"

repositories {
    listOf(
        "https://redirector.gvt1.com/edgedl/android/studio/ide-zips",
        "https://redirector.gvt1.com/edgedl/android/studio/install"
    ).forEach {
        ivy {
            url = uri(it)
            patternLayout {
                artifact("[revision]/[artifact]-[ext]")
                artifact("[revision]/[artifact]-[revision]-[ext]")
            }
            metadataSources { artifact() }
            content { includeGroup("android-studio") }
        }
    }
    ivy {
        url = uri("https://download.jetbrains.com/idea/")
        patternLayout {
            artifact("[artifact]-[revision].[ext]")
            // ARM Mac uses dash separator: ideaIC-2025.2-aarch64.dmg
            artifact("[artifact]-[revision]-[ext]")
        }
        metadataSources { artifact() }
        content { includeGroup("intellij-idea") }
    }
}

val androidStudioExtension = extensions.create<AndroidStudioTestExtension>("androidStudioTests").apply {
    autoDownloadAndroidStudio.convention(false)
    runAndroidStudioInHeadlessMode.convention(false)
    autoDownloadAndroidSdk.convention(false)
    testAndroidStudioCodename.convention("")
}

val intellijExtension = extensions.create<IntellijTestExtension>("intellijTests").apply {
    autoDownloadIntellij.convention(false)
    runIntellijInHeadlessMode.convention(false)
}

val androidStudioRuntime by configurations.creating
val intellijRuntime by configurations.creating

dependencies {
    val androidStudioFileExtension = when {
        isWindows() -> "windows.zip"
        isMacOS() && isIntel() -> "mac.dmg"
        isMacOS() && !isIntel() -> "mac_arm.dmg"
        isLinux() -> "linux.tar.gz"
        else -> throw IllegalStateException("Unsupported OS: $os")
    }
    androidStudioRuntime(androidStudioExtension.testAndroidStudioCodename.zip(androidStudioExtension.testAndroidStudioVersion) { codename, version ->
        val artifact = if (codename.isEmpty()) "android-studio" else "android-studio-$codename"
        "android-studio:$artifact:$version@$androidStudioFileExtension"
    })

    val intellijFileExtension = when {
        isWindows() -> "win.zip"
        isMacOS() && isIntel() -> "dmg"
        isMacOS() && !isIntel() -> "aarch64.dmg"
        isLinux() -> "tar.gz"
        else -> throw IllegalStateException("Unsupported OS: $os")
    }
    intellijRuntime(intellijExtension.testIntellijVersion.map { version ->
        "intellij-idea:ideaIC:$version@$intellijFileExtension"
    })
}

val unpackAndroidStudio = tasks.register<ExtractIdeTask>("unpackAndroidStudio") {
    volumeName.set("AndroidStudioForGradle")
    dmgAppName.set("Android Studio.app")
    ideDistribution.from(androidStudioRuntime)
    outputDir.set(layout.buildDirectory.dir("android-studio"))
}

val unpackIntellij = tasks.register<ExtractIdeTask>("unpackIntellij") {
    volumeName.set("IntellijIdeaForGradle")
    dmgAppName.set("IntelliJ IDEA CE.app")
    ideDistribution.from(intellijRuntime)
    outputDir.set(layout.buildDirectory.dir("intellij-idea"))
}

val installAndroidSdk = tasks.register<InstallAndroidSdkTask>("installAndroidSdk") {
    androidSdkVersion.set(androidStudioExtension.testAndroidSdkVersion)
    androidProjectDir.set(layout.buildDirectory.dir("installAndroidSdk/android-sdk-project"))
    val autoDownloadAndroidSdk = androidStudioExtension.autoDownloadAndroidSdk
    onlyIf { autoDownloadAndroidSdk.get() }
}

val androidStudioInstallation = objects.newInstance<IdeInstallation>().apply {
    installLocation.set(unpackAndroidStudio.flatMap { it.outputDir })
}

val intellijInstallation = objects.newInstance<IdeInstallation>().apply {
    installLocation.set(unpackIntellij.flatMap { it.outputDir })
}

tasks.withType<Test>().configureEach {
    dependsOn(installAndroidSdk)
    jvmArgumentProviders.add(
        IdeSystemProperties(
            androidStudioInstallation,
            androidStudioExtension.autoDownloadAndroidStudio,
            androidStudioExtension.runAndroidStudioInHeadlessMode,
            "studio.home",
            providers
        )
    )
    jvmArgumentProviders.add(
        IdeSystemProperties(
            intellijInstallation,
            intellijExtension.autoDownloadIntellij,
            intellijExtension.runIntellijInHeadlessMode,
            "idea.home",
            providers
        )
    )
}

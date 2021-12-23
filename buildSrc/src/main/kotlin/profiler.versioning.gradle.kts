import extensions.VersionInfo
import plugins.RootProjectVersionPlugin

rootProject.plugins.apply(RootProjectVersionPlugin::class)

val versionInfo = rootProject.extensions["versionInfo"] as VersionInfo
version = versionInfo.version.get()

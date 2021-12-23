import extensions.VersionInfo

rootProject.plugins.apply("profiler.versioning-root-project")

val versionInfo = rootProject.extensions["versionInfo"] as VersionInfo
version = versionInfo.version.get()

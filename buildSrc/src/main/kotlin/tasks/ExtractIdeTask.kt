package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject


@DisableCachingByDefault(because = "Not worth caching")
abstract class ExtractIdeTask @Inject constructor(
    private val execOps: ExecOperations,
    private val fsOps: FileSystemOperations,
    private val fileOps: FileOperations
) : DefaultTask() {

    @get:Input
    abstract val dmgAppName: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val ideDistribution: ConfigurableFileCollection

    @get:Input
    abstract val stripTopLevelDirectory: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun extract() {
        val distributionFile = ideDistribution.singleFile
        when {
            distributionFile.name.endsWith(".dmg") -> extractDmg(distributionFile)
            else -> extractZipOrTar(distributionFile)
        }
    }

    private fun extractZipOrTar(distributionFile: File) {
        fsOps.copy {
            val src = when {
                distributionFile.name.endsWith(".tar.gz") -> fileOps.tarTree(distributionFile)
                else -> fileOps.zipTree(distributionFile)
            }

            from(src) {
                if (stripTopLevelDirectory.get()) {
                    eachFile {
                        // Remove top folder when unzipping, that way we get rid of .app folder that can cause issues on Mac
                        // where MacOS would kill the process right after start, issue: https://github.com/gradle/gradle-profiler/issues/469
                        val newSegments = relativePath.segments.drop(1).toTypedArray()
                        if (newSegments.isEmpty()) {
                            exclude()
                        } else {
                            @Suppress("SpreadOperator")
                            relativePath = RelativePath(true, *newSegments)
                        }
                    }
                }
            }
            includeEmptyDirs = false

            into(outputDir)
        }
    }

    private fun extractDmg(distributionFile: File) {
        val appName = dmgAppName.get()
        val volume = appName.replace(" ", "")
        val volumeDir = "/Volumes/$volume"
        require(!File(volumeDir).exists()) {
            "The directory $volumeDir already exists. Please unmount it via `hdiutil detach $volumeDir`."
        }

        try {
            execOps.exec {
                commandLine("hdiutil", "attach", distributionFile.absolutePath, "-mountpoint", volumeDir)
            }

            // The .app bundle name may differ across releases (e.g. "Android Studio.app"
            // for stable, "Android Studio Preview.app" for canary). Find the actual .app bundle.
            val appBundle = File(volumeDir).listFiles()
                ?.firstOrNull { it.name.endsWith(".app") && it.isDirectory }
                ?: error("No .app bundle found in $volumeDir")
            val contentsDir = "${appBundle.absolutePath}/Contents"

            outputDir.get().asFile.mkdirs()
            execOps.exec {
                commandLine("cp", "-r", contentsDir, outputDir.get().asFile.absolutePath)
            }
        } finally {
            execOps.exec {
                commandLine("hdiutil", "detach", volumeDir)
            }
        }
    }
}

package org.gradle.profiler.studio.runner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GradleDaemonControl {

    suspend fun stopDaemons(
        projectDir: File,
        gradleUserHome: String,
        onLine: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val wrapper = projectDir.resolve(if (isWindows()) "gradlew.bat" else "gradlew")
        if (!wrapper.canExecute()) {
            onLine("[studio] gradle wrapper not found at ${wrapper.absolutePath}, skipping daemon stop")
            return@withContext
        }
        val cmd = buildList {
            add(wrapper.absolutePath)
            add("--stop")
            if (gradleUserHome.isNotBlank()) {
                add("--gradle-user-home"); add(gradleUserHome)
            }
        }
        onLine("[studio] stopping gradle daemons: ${cmd.joinToString(" ")}")
        val process = ProcessBuilder(cmd)
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().useLines { it.forEach(onLine) }
        onLine("[studio] daemon stop exit=${process.waitFor()}")
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")
}

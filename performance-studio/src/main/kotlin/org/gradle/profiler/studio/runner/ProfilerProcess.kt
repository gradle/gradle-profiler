package org.gradle.profiler.studio.runner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gradle.profiler.studio.ProfilerHome
import org.gradle.profiler.studio.domain.ConfigDraft
import org.gradle.profiler.studio.domain.Mode
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ProfilerProcess private constructor(private val process: Process) {

    suspend fun streamOutput(onLine: (String) -> Unit) = withContext(Dispatchers.IO) {
        BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
            lines.forEach(onLine)
        }
    }

    suspend fun awaitExit(): Int = withContext(Dispatchers.IO) { process.waitFor() }

    fun cancel() {
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
    }

    companion object {
        fun spawn(
            projectDir: File,
            outputDir: File,
            scenarioFile: File,
            draft: ConfigDraft,
        ): ProfilerProcess {
            val profilerHome = ProfilerHome.locate()
                ?: error("Could not find bundled gradle-profiler binary")
            val binary = profilerHome.resolve("bin/gradle-profiler")

            val cmd = buildList {
                add(binary.absolutePath)
                when (draft.mode) {
                    Mode.Benchmark -> add("--benchmark")
                    Mode.Profile -> {
                        add("--profile")
                        add(draft.profiler.ifBlank { "jfr" })
                    }
                }
                add("--project-dir"); add(projectDir.absolutePath)
                add("--output-dir"); add(outputDir.absolutePath)
                add("--scenario-file"); add(scenarioFile.absolutePath)
                if (draft.gradleUserHome.isNotBlank()) {
                    add("--gradle-user-home"); add(draft.gradleUserHome)
                }
                add(HoconWriter.SCENARIO_NAME)
            }
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .directory(projectDir)
                .start()
            return ProfilerProcess(process)
        }
    }
}

fun CoroutineScope.streamLines(process: ProfilerProcess, onLine: (String) -> Unit) {
    launch { process.streamOutput(onLine) }
}

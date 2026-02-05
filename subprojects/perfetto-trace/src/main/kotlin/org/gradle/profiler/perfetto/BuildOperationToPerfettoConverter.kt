package org.gradle.profiler.perfetto

import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.util.stream.Stream

/**
 * Converts Gradle's build operation trace log into [Perfetto](https://perfetto.dev) trace
 */
object BuildOperationToPerfettoConverter {

    data class Result(
        val packetCount: Int,
        val buildScanUrl: String? = null
    )

    @JvmStatic
    fun convert(buildOperationsLog: File, perfettoOutput: File): Result {
        val summary = ChromeTraceWriter(perfettoOutput).use { converter ->
            streamOperations(buildOperationsLog).use { operations ->
                BuildOperationVisitor.visitLogs(operations, converter)
            }

            converter.getSummary()
        }

        return Result(summary.packetCount)
    }

    private fun streamOperations(buildOperationsLog: File): Stream<BuildOperationRecord> {
        try {
            val gson = Gson().newBuilder()
                .registerTypeAdapterFactory(BuildOperationRecordAdapterFactory())
                .create()
            return Files.lines(buildOperationsLog.toPath())
                .map { line -> gson.fromJson(line, BuildOperationRecord::class.java) }
        } catch (e: Exception) {
            throw RuntimeException("Failed to read build operation trace file: ${buildOperationsLog.absolutePath}", e)
        }
    }

}

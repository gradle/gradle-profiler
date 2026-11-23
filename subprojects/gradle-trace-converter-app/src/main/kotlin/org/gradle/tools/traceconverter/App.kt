package org.gradle.tools.traceconverter

import java.io.File
import kotlin.system.exitProcess
import org.gradle.profiler.perfetto.BuildOperationToPerfettoConverter
import org.gradle.profiler.perfetto.jfr.JfrToPerfettoConverter


fun main(args: Array<String>) {
    val exitCode = run(args)
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}

internal fun run(args: Array<String>): Int {
    if (args.isEmpty()) {
        System.err.println("Usage: gtc <trace-log.txt|recording.jfr>")
        return 1
    }
    val inputFile = File(args[0])
    if (!inputFile.isFile) {
        System.err.println("File not found: ${inputFile.absolutePath}")
        return 1
    }

    val outputFile = outputFile(inputFile)
    return when {
        inputFile.name.endsWith(".jfr") -> {
            JfrToPerfettoConverter.convert(inputFile, outputFile)
            println("Written trace to ${outputFile.absolutePath}")
            0
        }

        else -> {
            val result = BuildOperationToPerfettoConverter.convert(inputFile, outputFile)
            println("Written trace to ${outputFile.absolutePath}")
            if (result.buildScanUrl != null) {
                println("Build scan URL: ${result.buildScanUrl}")
            }
            0
        }
    }
}

private fun outputFile(traceFile: File): File {
    val baseName = traceFile.nameWithoutExtension
        .removeSuffix("-log") // always appended by Gradle when writing a build operations log
    return File(traceFile.parentFile, "$baseName.perfetto.proto")
}

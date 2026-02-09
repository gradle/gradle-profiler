package org.gradle.tools.traceconverter

import java.io.File
import kotlin.system.exitProcess
import org.gradle.profiler.perfetto.BuildOperationToPerfettoConverter


fun main(args: Array<String>) {
    val exitCode = run(args)
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}

internal fun run(args: Array<String>): Int {
    if (args.isEmpty()) {
        System.err.println("Usage: gtc <build-operations-trace-log>")
        return 1
    }
    val buildOperationsLog = File(args[0])
    if (!buildOperationsLog.isFile) {
        System.err.println("File not found: ${buildOperationsLog.absolutePath}")
        return 1
    }
    val outputFile = outputFile(buildOperationsLog)

    val result = BuildOperationToPerfettoConverter.convert(buildOperationsLog, outputFile)
    println("Written ${result.packetCount} packets to ${outputFile.absolutePath}")
    if (result.buildScanUrl != null) {
        println("Build scan URL: ${result.buildScanUrl}")
    }
    return 0
}

private fun outputFile(traceFile: File): File {
    val baseName = traceFile.nameWithoutExtension
        .removeSuffix("-log") // always appended by Gradle when writing a build operations log
    return File(traceFile.parentFile, "$baseName.perfetto.proto")
}

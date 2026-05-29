package org.gradle.profiler.studio.data

import java.time.Instant

data class Run(
    val id: Int,
    val projectId: Int,
    val outputName: String,
    val outputDir: String,
    val status: RunStatus,
    val startedAt: Instant,
    val endedAt: Instant?,
    val exitCode: Int?,
)

enum class RunStatus { Running, Success, Failure, Cancelled }

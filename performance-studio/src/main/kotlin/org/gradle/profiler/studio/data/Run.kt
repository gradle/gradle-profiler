package org.gradle.profiler.studio.data

import org.gradle.profiler.studio.domain.ConfigDraft
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
    val config: ConfigDraft?,
)

enum class RunStatus { Running, Success, Failure, Cancelled }

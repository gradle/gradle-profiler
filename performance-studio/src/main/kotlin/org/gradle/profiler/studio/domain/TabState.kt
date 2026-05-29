package org.gradle.profiler.studio.domain

import java.io.File

data class TabState(
    val id: Long,
    val status: TabStatus,
    val config: ConfigDraft,
    val runId: Int? = null,
    val outputName: String? = null,
    val outputDir: File? = null,
) {
    val displayName: String get() = outputName ?: "New configuration"
}

enum class TabStatus { Editing, Running, Success, Failure, Cancelled }

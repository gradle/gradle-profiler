package org.gradle.profiler.studio.domain

import java.io.File

data class TabState(
    val id: Long,
    val status: TabStatus,
    val config: ConfigDraft,
    val section: TabSection = TabSection.Scenario,
    val runId: Int? = null,
    val outputName: String? = null,
    val outputDir: File? = null,
) {
    val displayName: String get() = outputName ?: "New configuration"
}

enum class TabStatus { Editing, Running, Success, Failure, Cancelled }

enum class TabSection(val label: String) {
    Scenario("Scenario"),
    Console("Console"),
    Results("Results"),
}

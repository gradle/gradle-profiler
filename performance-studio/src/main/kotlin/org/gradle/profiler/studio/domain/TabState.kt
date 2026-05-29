package org.gradle.profiler.studio.domain

data class TabState(
    val id: Long,
    val name: String,
    val status: TabStatus,
    val config: ConfigDraft,
)

enum class TabStatus { Editing, Running, Success, Failure, Cancelled }

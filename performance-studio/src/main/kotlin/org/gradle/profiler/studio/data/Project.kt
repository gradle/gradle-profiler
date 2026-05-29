package org.gradle.profiler.studio.data

import java.time.Instant

data class Project(
    val id: Int,
    val name: String,
    val path: String,
    val createdAt: Instant,
)

enum class ProjectStatus { Idle, Running, Success, Failure }

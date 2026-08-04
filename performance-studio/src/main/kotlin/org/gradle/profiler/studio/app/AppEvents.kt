package org.gradle.profiler.studio.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.gradle.profiler.studio.data.Project

class AppEvents {
    val projectAdded = MutableSharedFlow<Project>(extraBufferCapacity = 8)
    val projectRemoved = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val runCompleted = MutableSharedFlow<Int>(extraBufferCapacity = 8) // projectId
    val openRunRequested = MutableSharedFlow<OpenRunRequest>(extraBufferCapacity = 8)
    val projectSelected = MutableStateFlow<Int?>(null)
    val runningProjects = MutableStateFlow<Set<Int>>(emptySet())
}

data class OpenRunRequest(val projectId: Int, val runId: Int)

package org.gradle.profiler.studio.app

import org.gradle.profiler.studio.data.Project
import org.gradle.profiler.studio.data.ProjectStatus
import org.gradle.profiler.studio.data.Run
import org.gradle.profiler.studio.data.RunStatus
import org.gradle.profiler.studio.domain.TabState
import org.gradle.profiler.studio.domain.TabStatus

data class AppState(
    val projects: List<Project> = emptyList(),
    val selectedProjectId: Int? = null,
    val tabsByProject: Map<Int, List<TabState>> = emptyMap(),
    val selectedTabByProject: Map<Int, Long> = emptyMap(),
    val expandedProjects: Set<Int> = emptySet(),
    val runsByProject: Map<Int, List<Run>> = emptyMap(),
) {
    fun projectStatuses(): Map<Int, ProjectStatus> = projects.associate { p ->
        val pTabs = tabsByProject[p.id].orEmpty()
        val latestRun = runsByProject[p.id]?.firstOrNull()
        p.id to when {
            pTabs.any { it.status == TabStatus.Running } -> ProjectStatus.Running
            pTabs.any { it.status == TabStatus.Failure } -> ProjectStatus.Failure
            pTabs.any { it.status == TabStatus.Success } -> ProjectStatus.Success
            latestRun?.status == RunStatus.Failure -> ProjectStatus.Failure
            latestRun?.status == RunStatus.Success -> ProjectStatus.Success
            else -> ProjectStatus.Idle
        }
    }

    fun tabOf(projectId: Int, tabId: Long): TabState? =
        tabsByProject[projectId]?.firstOrNull { it.id == tabId }

    fun projectOf(projectId: Int): Project? =
        projects.firstOrNull { it.id == projectId }
}

package org.gradle.profiler.studio.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.gradle.profiler.studio.data.Project
import org.gradle.profiler.studio.data.ProjectRepository
import org.gradle.profiler.studio.data.ProjectStatus
import org.gradle.profiler.studio.domain.ConfigDraft
import org.gradle.profiler.studio.domain.TabState
import org.gradle.profiler.studio.domain.TabStatus
import java.io.File

class AppState(private val projectRepo: ProjectRepository) {
    val projects: StateFlow<List<Project>> = projectRepo.projects

    private val _selectedProjectId = MutableStateFlow<Int?>(null)
    val selectedProjectId: StateFlow<Int?> = _selectedProjectId.asStateFlow()

    private val _tabsByProject = MutableStateFlow<Map<Int, List<TabState>>>(emptyMap())
    val tabsByProject: StateFlow<Map<Int, List<TabState>>> = _tabsByProject.asStateFlow()

    private val _selectedTabByProject = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val selectedTabByProject: StateFlow<Map<Int, Long>> = _selectedTabByProject.asStateFlow()

    private val tabCounters = mutableMapOf<Int, Int>()

    fun selectProject(id: Int) {
        _selectedProjectId.value = id
    }

    fun addProject(folder: File) {
        val project = projectRepo.add(folder.name, folder.absolutePath)
        _selectedProjectId.value = project.id
    }

    fun statusFor(@Suppress("UNUSED_PARAMETER") project: Project): ProjectStatus = ProjectStatus.Idle

    fun newTab(projectId: Int): TabState {
        val n = (tabCounters[projectId] ?: 0) + 1
        tabCounters[projectId] = n
        val tab = TabState(
            id = System.nanoTime(),
            name = "profiler-out-$n",
            status = TabStatus.Editing,
            config = ConfigDraft(),
        )
        _tabsByProject.update { map ->
            map + (projectId to ((map[projectId] ?: emptyList()) + tab))
        }
        _selectedTabByProject.update { it + (projectId to tab.id) }
        return tab
    }

    fun selectTab(projectId: Int, tabId: Long) {
        _selectedTabByProject.update { it + (projectId to tabId) }
    }

    fun closeTab(projectId: Int, tabId: Long) {
        _tabsByProject.update { map ->
            val list = (map[projectId] ?: return@update map).filterNot { it.id == tabId }
            map + (projectId to list)
        }
        _selectedTabByProject.update { sel ->
            if (sel[projectId] != tabId) sel
            else {
                val remaining = _tabsByProject.value[projectId].orEmpty()
                if (remaining.isEmpty()) sel - projectId else sel + (projectId to remaining.last().id)
            }
        }
    }

    fun updateConfig(projectId: Int, tabId: Long, transform: (ConfigDraft) -> ConfigDraft) {
        _tabsByProject.update { map ->
            val list = (map[projectId] ?: return@update map).map { tab ->
                if (tab.id == tabId) tab.copy(config = transform(tab.config)) else tab
            }
            map + (projectId to list)
        }
    }
}

package org.gradle.profiler.studio.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gradle.profiler.studio.data.Project
import org.gradle.profiler.studio.data.ProjectRepository
import org.gradle.profiler.studio.data.ProjectStatus
import java.io.File

class AppState(private val projectRepo: ProjectRepository) {
    val projects: StateFlow<List<Project>> = projectRepo.projects

    private val _selectedProjectId = MutableStateFlow<Int?>(null)
    val selectedProjectId: StateFlow<Int?> = _selectedProjectId.asStateFlow()

    fun selectProject(id: Int) {
        _selectedProjectId.value = id
    }

    fun addProject(folder: File) {
        val project = projectRepo.add(folder.name, folder.absolutePath)
        _selectedProjectId.value = project.id
    }

    fun statusFor(@Suppress("UNUSED_PARAMETER") project: Project): ProjectStatus = ProjectStatus.Idle
}

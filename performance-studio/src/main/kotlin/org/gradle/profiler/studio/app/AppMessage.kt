package org.gradle.profiler.studio.app

import org.gradle.profiler.studio.data.Project
import org.gradle.profiler.studio.data.Run
import org.gradle.profiler.studio.domain.ConfigDraft
import org.gradle.profiler.studio.domain.TabSection
import org.gradle.profiler.studio.domain.TabState
import java.io.File

sealed interface AppMessage {
    data object LoadProjects : AppMessage
    data class ProjectsLoaded(val projects: List<Project>) : AppMessage
    data class RunsLoaded(val projectId: Int, val runs: List<Run>) : AppMessage

    data class AddProject(val folder: File) : AppMessage
    data class ProjectAdded(val project: Project) : AppMessage
    data class RemoveProject(val projectId: Int) : AppMessage
    data class ProjectRemoved(val projectId: Int) : AppMessage
    data class SelectProject(val projectId: Int) : AppMessage
    data class ToggleProjectExpanded(val projectId: Int) : AppMessage

    data class NewTab(val projectId: Int) : AppMessage
    data class SelectTab(val projectId: Int, val tabId: Long) : AppMessage
    data class CloseTab(val projectId: Int, val tabId: Long) : AppMessage
    data class SelectSection(val projectId: Int, val tabId: Long, val section: TabSection) : AppMessage
    data class UpdateConfig(val projectId: Int, val tabId: Long, val config: ConfigDraft) : AppMessage

    data class OpenRunInTab(val projectId: Int, val runId: Int) : AppMessage
    data class RunTabRestored(val projectId: Int, val tab: TabState) : AppMessage

    data class StartRun(val projectId: Int, val tabId: Long) : AppMessage
    data class RunStarted(
        val projectId: Int,
        val tabId: Long,
        val runId: Int,
        val outputName: String,
        val outputDir: File,
    ) : AppMessage
    data class RunSpawnFailed(val projectId: Int, val tabId: Long, val runId: Int) : AppMessage
    data class RunCompleted(
        val projectId: Int,
        val tabId: Long,
        val exit: Int,
        val cancelled: Boolean,
    ) : AppMessage
    data class CancelRun(val projectId: Int, val tabId: Long) : AppMessage
}

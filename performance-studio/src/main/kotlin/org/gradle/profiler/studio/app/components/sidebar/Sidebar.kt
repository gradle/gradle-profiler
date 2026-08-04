package org.gradle.profiler.studio.app.components.sidebar

import kotlinx.coroutines.flow.map
import org.gradle.profiler.studio.app.AppDeps
import org.gradle.profiler.studio.data.AppPaths
import org.gradle.profiler.studio.data.Project
import org.gradle.profiler.studio.data.Run
import org.gradle.profiler.studio.mvu.Component
import org.gradle.profiler.studio.mvu.Effekt
import org.gradle.profiler.studio.mvu.Upd
import org.gradle.profiler.studio.mvu.noEffects
import org.gradle.profiler.studio.mvu.with
import java.io.File

data class SidebarState(
    val projects: List<Project> = emptyList(),
    val expandedProjects: Set<Int> = emptySet(),
    val runsByProject: Map<Int, List<Run>> = emptyMap(),
    val selectedProjectId: Int? = null,
)

sealed interface SidebarMessage {
    data class ProjectsLoaded(val projects: List<Project>) : SidebarMessage
    data class RunsLoaded(val projectId: Int, val runs: List<Run>) : SidebarMessage
    data class AddProjectRequested(val folder: File) : SidebarMessage
    data class ProjectAddedInternally(val project: Project) : SidebarMessage
    data class RemoveProjectRequested(val projectId: Int) : SidebarMessage
    data class ProjectRemovedInternally(val projectId: Int) : SidebarMessage
    data class ToggleExpand(val projectId: Int) : SidebarMessage
    data class SelectProject(val projectId: Int) : SidebarMessage
    data class OpenRunRequested(val projectId: Int, val runId: Int) : SidebarMessage

    data class ProjectSelectedExternally(val projectId: Int?) : SidebarMessage
    data class RunCompletedExternally(val projectId: Int) : SidebarMessage
}

private val sidebarInitial: Upd<SidebarState, SidebarMessage, AppDeps> =
    SidebarState() with setOf(
        Effekt.single { deps -> SidebarMessage.ProjectsLoaded(deps.projects.list()) },
        Effekt.flow { deps -> deps.events.projectSelected.map(SidebarMessage::ProjectSelectedExternally) },
        Effekt.flow { deps -> deps.events.runCompleted.map(SidebarMessage::RunCompletedExternally) },
    )

private fun sidebarUpdate(
    msg: SidebarMessage,
    state: SidebarState,
): Upd<SidebarState, SidebarMessage, AppDeps> = when (msg) {
    is SidebarMessage.ProjectsLoaded -> state.copy(projects = msg.projects) with
        msg.projects.mapTo(mutableSetOf()) { p ->
            Effekt.single { deps -> SidebarMessage.RunsLoaded(p.id, deps.runs.listForProject(p.id)) }
        }

    is SidebarMessage.RunsLoaded -> state.copy(
        runsByProject = state.runsByProject + (msg.projectId to msg.runs),
    ) with noEffects()

    is SidebarMessage.AddProjectRequested -> state with Effekt.single { deps ->
        val p = deps.projects.add(msg.folder.name, msg.folder.absolutePath)
        deps.events.projectAdded.emit(p)
        deps.events.projectSelected.value = p.id
        SidebarMessage.ProjectAddedInternally(p)
    }

    is SidebarMessage.ProjectAddedInternally -> state.copy(
        projects = (state.projects + msg.project).sortedBy { it.name.lowercase() },
    ) with noEffects()

    is SidebarMessage.RemoveProjectRequested -> state with Effekt.single { deps ->
        deps.runs.deleteForProject(msg.projectId)
        deps.projects.remove(msg.projectId)
        val dir = AppPaths.runsDir.resolve(msg.projectId.toString())
        if (dir.exists()) dir.deleteRecursively()
        if (deps.events.projectSelected.value == msg.projectId) deps.events.projectSelected.value = null
        deps.events.projectRemoved.emit(msg.projectId)
        SidebarMessage.ProjectRemovedInternally(msg.projectId)
    }

    is SidebarMessage.ProjectRemovedInternally -> state.copy(
        projects = state.projects.filterNot { it.id == msg.projectId },
        expandedProjects = state.expandedProjects - msg.projectId,
        runsByProject = state.runsByProject - msg.projectId,
    ) with noEffects()

    is SidebarMessage.ToggleExpand -> state.copy(
        expandedProjects = if (msg.projectId in state.expandedProjects)
            state.expandedProjects - msg.projectId
        else state.expandedProjects + msg.projectId,
    ) with noEffects()

    is SidebarMessage.SelectProject -> state with Effekt.idle { deps ->
        deps.events.projectSelected.value = msg.projectId
    }

    is SidebarMessage.OpenRunRequested -> state with Effekt.idle { deps ->
        deps.events.openRunRequested.emit(
            org.gradle.profiler.studio.app.OpenRunRequest(msg.projectId, msg.runId),
        )
        deps.events.projectSelected.value = msg.projectId
    }

    is SidebarMessage.ProjectSelectedExternally -> state.copy(selectedProjectId = msg.projectId) with noEffects()

    is SidebarMessage.RunCompletedExternally -> state with Effekt.single { deps ->
        SidebarMessage.RunsLoaded(msg.projectId, deps.runs.listForProject(msg.projectId))
    }
}

class SidebarComponent(deps: AppDeps) : Component<SidebarState, SidebarMessage, AppDeps>(
    initial = sidebarInitial,
    update = ::sidebarUpdate,
    dependencies = deps,
    onError = { it.printStackTrace() },
)

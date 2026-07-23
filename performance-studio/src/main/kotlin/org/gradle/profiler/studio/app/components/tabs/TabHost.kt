package org.gradle.profiler.studio.app.components.tabs

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.gradle.profiler.studio.app.AppDeps
import org.gradle.profiler.studio.data.AppPaths
import org.gradle.profiler.studio.data.RunStatus
import org.gradle.profiler.studio.domain.ConfigDraft
import org.gradle.profiler.studio.domain.TabSection
import org.gradle.profiler.studio.domain.TabState
import org.gradle.profiler.studio.domain.TabStatus
import org.gradle.profiler.studio.mvu.Component
import org.gradle.profiler.studio.mvu.Effekt
import org.gradle.profiler.studio.mvu.Upd
import org.gradle.profiler.studio.mvu.noEffects
import org.gradle.profiler.studio.mvu.with
import org.gradle.profiler.studio.runner.ConsoleBuffer
import org.gradle.profiler.studio.runner.GradleDaemonControl
import org.gradle.profiler.studio.runner.HoconWriter
import org.gradle.profiler.studio.runner.ProfilerProcess
import java.io.File

private const val STUDIO_LOG = "studio.log"

data class TabHostState(
    val tabsByProject: Map<Int, List<TabState>> = emptyMap(),
    val selectedTabByProject: Map<Int, Long> = emptyMap(),
    val selectedProjectId: Int? = null,
)

sealed interface TabHostMessage {
    data class NewTab(val projectId: Int) : TabHostMessage
    data class SelectTab(val projectId: Int, val tabId: Long) : TabHostMessage
    data class CloseTab(val projectId: Int, val tabId: Long) : TabHostMessage
    data class SelectSection(val projectId: Int, val tabId: Long, val section: TabSection) : TabHostMessage
    data class UpdateConfig(val projectId: Int, val tabId: Long, val config: ConfigDraft) : TabHostMessage

    data class StartRun(val projectId: Int, val tabId: Long) : TabHostMessage
    data class RunStarted(
        val projectId: Int,
        val tabId: Long,
        val runId: Int,
        val outputName: String,
        val outputDir: File,
    ) : TabHostMessage
    data class RunSpawnFailed(val projectId: Int, val tabId: Long) : TabHostMessage
    data class RunCompleted(
        val projectId: Int,
        val tabId: Long,
        val exit: Int,
        val cancelled: Boolean,
    ) : TabHostMessage
    data class CancelRun(val projectId: Int, val tabId: Long) : TabHostMessage

    data class RunTabRestored(val projectId: Int, val tab: TabState) : TabHostMessage

    data class ProjectSelectedExternally(val projectId: Int) : TabHostMessage
    data class ProjectRemovedExternally(val projectId: Int) : TabHostMessage
    data class OpenRunRequestedExternally(val projectId: Int, val runId: Int) : TabHostMessage
}

private val tabHostInitial: Upd<TabHostState, TabHostMessage, AppDeps> =
    TabHostState() with setOf(
        Effekt.flow { deps ->
            deps.events.projectSelected.filterNotNull().map(TabHostMessage::ProjectSelectedExternally)
        },
        Effekt.flow { deps ->
            deps.events.projectRemoved.map(TabHostMessage::ProjectRemovedExternally)
        },
        Effekt.flow { deps ->
            deps.events.openRunRequested.map {
                TabHostMessage.OpenRunRequestedExternally(it.projectId, it.runId)
            }
        },
    )

private fun tabHostUpdate(
    msg: TabHostMessage,
    state: TabHostState,
): Upd<TabHostState, TabHostMessage, AppDeps> = when (msg) {
    is TabHostMessage.NewTab -> {
        val tab = TabState(
            id = System.nanoTime(),
            status = TabStatus.Editing,
            config = ConfigDraft(),
        )
        state.copy(
            tabsByProject = state.tabsByProject.appendTab(msg.projectId, tab),
            selectedTabByProject = state.selectedTabByProject + (msg.projectId to tab.id),
        ) with noEffects()
    }

    is TabHostMessage.SelectTab -> state.copy(
        selectedTabByProject = state.selectedTabByProject + (msg.projectId to msg.tabId),
    ) with noEffects()

    is TabHostMessage.CloseTab -> closeTab(msg, state)

    is TabHostMessage.SelectSection -> state.updateTab(msg.projectId, msg.tabId) {
        it.copy(section = msg.section)
    } with noEffects()

    is TabHostMessage.UpdateConfig -> state.updateTab(msg.projectId, msg.tabId) {
        it.copy(config = msg.config)
    } with noEffects()

    is TabHostMessage.StartRun -> startRun(msg, state)

    is TabHostMessage.RunStarted -> {
        val newRunning = state.runningProjectsAfterStart(msg.projectId)
        state.updateTab(msg.projectId, msg.tabId) {
            it.copy(
                status = TabStatus.Running,
                section = TabSection.Console,
                runId = msg.runId,
                outputName = msg.outputName,
                outputDir = msg.outputDir,
            )
        } with Effekt.idle { deps -> deps.events.runningProjects.value = newRunning }
    }

    is TabHostMessage.RunSpawnFailed -> {
        val newState = state.updateTab(msg.projectId, msg.tabId) { it.copy(status = TabStatus.Failure) }
        val newRunning = newState.computeRunningProjects()
        newState with Effekt.idle { deps ->
            deps.events.runningProjects.value = newRunning
            deps.events.runCompleted.emit(msg.projectId)
        }
    }

    is TabHostMessage.RunCompleted -> runCompleted(msg, state)

    is TabHostMessage.CancelRun -> cancelRun(msg, state)

    is TabHostMessage.RunTabRestored -> state.copy(
        tabsByProject = state.tabsByProject.appendTab(msg.projectId, msg.tab),
        selectedTabByProject = state.selectedTabByProject + (msg.projectId to msg.tab.id),
    ) with noEffects()

    is TabHostMessage.ProjectSelectedExternally -> state.copy(selectedProjectId = msg.projectId) with noEffects()

    is TabHostMessage.ProjectRemovedExternally -> state.copy(
        tabsByProject = state.tabsByProject - msg.projectId,
        selectedTabByProject = state.selectedTabByProject - msg.projectId,
        selectedProjectId = if (state.selectedProjectId == msg.projectId) null else state.selectedProjectId,
    ) with Effekt.idle { deps ->
        state.tabsByProject[msg.projectId].orEmpty().forEach { tab ->
            deps.processes.cancel(tab.id)
            deps.processes.remove(tab.id)
            deps.consoles.remove(tab.id)
        }
    }

    is TabHostMessage.OpenRunRequestedExternally -> openRunInTab(msg, state)
}

private fun startRun(msg: TabHostMessage.StartRun, state: TabHostState): Upd<TabHostState, TabHostMessage, AppDeps> {
    val tab = state.tabOf(msg.projectId, msg.tabId) ?: return state with noEffects()
    if (tab.status == TabStatus.Running) return state with noEffects()
    return state with Effekt.flow<AppDeps, TabHostMessage> { deps ->
        flow {
            val project = deps.projects.list().firstOrNull { it.id == msg.projectId } ?: return@flow
            val outputName = deps.runs.nextOutputName(project.id)
            val outputDir = AppPaths.runsDir
                .resolve(project.id.toString())
                .resolve(outputName)
                .apply { mkdirs() }
            val scenarioFile = outputDir.resolve("scenario.conf")
            HoconWriter.write(tab.config, scenarioFile)
            val run = deps.runs.create(project.id, outputName, outputDir.absolutePath, tab.config)
            val console = deps.consoles.get(msg.tabId)
            console.clear()

            emit(TabHostMessage.RunStarted(project.id, msg.tabId, run.id, outputName, outputDir))

            val process = try {
                ProfilerProcess.spawn(File(project.path), outputDir, scenarioFile, tab.config)
            } catch (e: Exception) {
                console.append("Failed to start profiler: ${e.message}")
                deps.runs.finish(run.id, RunStatus.Failure, null)
                emit(TabHostMessage.RunSpawnFailed(project.id, msg.tabId))
                return@flow
            }
            deps.processes.put(msg.tabId, process)

            val exit = coroutineScope {
                launch {
                    outputDir.resolve(STUDIO_LOG).bufferedWriter().use { writer ->
                        process.streamOutput { line ->
                            console.append(line)
                            writer.appendLine(line)
                        }
                    }
                }
                process.awaitExit()
            }
            val cancelled = deps.processes.wasCancelled(msg.tabId)
            deps.processes.remove(msg.tabId)
            emit(TabHostMessage.RunCompleted(project.id, msg.tabId, exit, cancelled))
        }
    }
}

private fun runCompleted(
    msg: TabHostMessage.RunCompleted,
    state: TabHostState,
): Upd<TabHostState, TabHostMessage, AppDeps> {
    val newTabStatus = when {
        msg.cancelled -> TabStatus.Cancelled
        msg.exit == 0 -> TabStatus.Success
        else -> TabStatus.Failure
    }
    val runStatus = when {
        msg.cancelled -> RunStatus.Cancelled
        msg.exit == 0 -> RunStatus.Success
        else -> RunStatus.Failure
    }
    val runId = state.tabOf(msg.projectId, msg.tabId)?.runId
    val newState = state.updateTab(msg.projectId, msg.tabId) { it.copy(status = newTabStatus) }
    val newRunning = newState.computeRunningProjects()
    return newState with Effekt.idle { deps ->
        runId?.let { deps.runs.finish(it, runStatus, msg.exit) }
        deps.events.runningProjects.value = newRunning
        deps.events.runCompleted.emit(msg.projectId)
    }
}

private fun cancelRun(
    msg: TabHostMessage.CancelRun,
    state: TabHostState,
): Upd<TabHostState, TabHostMessage, AppDeps> {
    val tab = state.tabOf(msg.projectId, msg.tabId) ?: return state with noEffects()
    val newState = if (tab.status == TabStatus.Running) {
        state.updateTab(msg.projectId, msg.tabId) { it.copy(status = TabStatus.Cancelled) }
    } else state
    return newState with Effekt.idle<AppDeps, TabHostMessage> { deps ->
        deps.processes.cancel(msg.tabId)
        val project = deps.projects.list().firstOrNull { it.id == msg.projectId } ?: return@idle
        val console = deps.consoles.get(msg.tabId)
        GradleDaemonControl.stopDaemons(File(project.path), tab.config.gradleUserHome) {
            console.append(it)
        }
    }
}

private fun closeTab(
    msg: TabHostMessage.CloseTab,
    state: TabHostState,
): Upd<TabHostState, TabHostMessage, AppDeps> {
    val tabs = state.tabsByProject[msg.projectId].orEmpty()
    val tab = tabs.firstOrNull { it.id == msg.tabId }
    val wasRunning = tab?.status == TabStatus.Running

    val remaining = tabs.filterNot { it.id == msg.tabId }
    val sel = state.selectedTabByProject
    val newSel = when {
        sel[msg.projectId] != msg.tabId -> sel
        remaining.isEmpty() -> sel - msg.projectId
        else -> sel + (msg.projectId to remaining.last().id)
    }

    val newState = state.copy(
        tabsByProject = state.tabsByProject + (msg.projectId to remaining),
        selectedTabByProject = newSel,
    )
    return newState with Effekt.idle { deps ->
        if (wasRunning) {
            deps.processes.cancel(msg.tabId)
            val project = deps.projects.list().firstOrNull { it.id == msg.projectId }
            if (project != null && tab != null) {
                val console = deps.consoles.get(msg.tabId)
                GradleDaemonControl.stopDaemons(File(project.path), tab.config.gradleUserHome) {
                    console.append(it)
                }
            }
            deps.events.runningProjects.value = newState.computeRunningProjects()
        }
        deps.processes.remove(msg.tabId)
        deps.consoles.remove(msg.tabId)
    }
}

private fun openRunInTab(
    msg: TabHostMessage.OpenRunRequestedExternally,
    state: TabHostState,
): Upd<TabHostState, TabHostMessage, AppDeps> {
    val existing = state.tabsByProject[msg.projectId]?.firstOrNull { it.runId == msg.runId }
    if (existing != null) {
        return state.copy(
            selectedTabByProject = state.selectedTabByProject + (msg.projectId to existing.id),
        ) with noEffects()
    }
    return state with Effekt.flow<AppDeps, TabHostMessage> { deps ->
        flow {
            val run = deps.runs.findById(msg.runId) ?: return@flow
            val tabStatus = when (run.status) {
                RunStatus.Running -> TabStatus.Editing
                RunStatus.Success -> TabStatus.Success
                RunStatus.Failure -> TabStatus.Failure
                RunStatus.Cancelled -> TabStatus.Cancelled
            }
            val tab = TabState(
                id = System.nanoTime(),
                status = tabStatus,
                config = run.config ?: ConfigDraft(),
                section = TabSection.Console,
                runId = run.id,
                outputName = run.outputName,
                outputDir = File(run.outputDir),
            )
            val buffer = deps.consoles.get(tab.id)
            val logFile = File(run.outputDir).resolve(STUDIO_LOG)
            if (logFile.exists()) buffer.loadAll(logFile.readLines())
            emit(TabHostMessage.RunTabRestored(msg.projectId, tab))
        }
    }
}

private fun TabHostState.tabOf(projectId: Int, tabId: Long): TabState? =
    tabsByProject[projectId]?.firstOrNull { it.id == tabId }

private fun TabHostState.updateTab(
    projectId: Int,
    tabId: Long,
    transform: (TabState) -> TabState,
): TabHostState {
    val list = tabsByProject[projectId] ?: return this
    return copy(tabsByProject = tabsByProject + (projectId to list.map {
        if (it.id == tabId) transform(it) else it
    }))
}

private fun Map<Int, List<TabState>>.appendTab(projectId: Int, tab: TabState): Map<Int, List<TabState>> =
    this + (projectId to ((this[projectId] ?: emptyList()) + tab))

private fun TabHostState.computeRunningProjects(): Set<Int> =
    tabsByProject.entries.mapNotNullTo(mutableSetOf()) { (pid, tabs) ->
        if (tabs.any { it.status == TabStatus.Running }) pid else null
    }

private fun TabHostState.runningProjectsAfterStart(projectId: Int): Set<Int> {
    // recompute after the update to RunStarted that this is being called for; the state already
    // contains the Running tab via updateTab in the caller chain.
    return computeRunningProjects() + projectId
}

class TabHostComponent(private val deps: AppDeps) : Component<TabHostState, TabHostMessage, AppDeps>(
    initial = tabHostInitial,
    update = ::tabHostUpdate,
    dependencies = deps,
    onError = { it.printStackTrace() },
) {
    fun consoleFor(tabId: Long): ConsoleBuffer = deps.consoles.get(tabId)
}

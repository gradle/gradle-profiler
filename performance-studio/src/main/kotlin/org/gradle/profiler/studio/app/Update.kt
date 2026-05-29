package org.gradle.profiler.studio.app

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.gradle.profiler.studio.app.AppMessage.AddProject
import org.gradle.profiler.studio.app.AppMessage.CancelRun
import org.gradle.profiler.studio.app.AppMessage.CloseTab
import org.gradle.profiler.studio.app.AppMessage.LoadProjects
import org.gradle.profiler.studio.app.AppMessage.NewTab
import org.gradle.profiler.studio.app.AppMessage.OpenRunInTab
import org.gradle.profiler.studio.app.AppMessage.ProjectAdded
import org.gradle.profiler.studio.app.AppMessage.ProjectRemoved
import org.gradle.profiler.studio.app.AppMessage.ProjectsLoaded
import org.gradle.profiler.studio.app.AppMessage.RemoveProject
import org.gradle.profiler.studio.app.AppMessage.RunCompleted
import org.gradle.profiler.studio.app.AppMessage.RunSpawnFailed
import org.gradle.profiler.studio.app.AppMessage.RunStarted
import org.gradle.profiler.studio.app.AppMessage.RunTabRestored
import org.gradle.profiler.studio.app.AppMessage.RunsLoaded
import org.gradle.profiler.studio.app.AppMessage.SelectProject
import org.gradle.profiler.studio.app.AppMessage.SelectSection
import org.gradle.profiler.studio.app.AppMessage.SelectTab
import org.gradle.profiler.studio.app.AppMessage.StartRun
import org.gradle.profiler.studio.app.AppMessage.ToggleProjectExpanded
import org.gradle.profiler.studio.app.AppMessage.UpdateConfig
import org.gradle.profiler.studio.data.AppPaths
import org.gradle.profiler.studio.data.RunStatus
import org.gradle.profiler.studio.domain.ConfigDraft
import org.gradle.profiler.studio.domain.TabSection
import org.gradle.profiler.studio.domain.TabState
import org.gradle.profiler.studio.domain.TabStatus
import org.gradle.profiler.studio.mvu.Effekt
import org.gradle.profiler.studio.mvu.Upd
import org.gradle.profiler.studio.mvu.noEffects
import org.gradle.profiler.studio.mvu.with
import org.gradle.profiler.studio.runner.GradleDaemonControl
import org.gradle.profiler.studio.runner.HoconWriter
import org.gradle.profiler.studio.runner.ProfilerProcess
import java.io.File

private const val STUDIO_LOG = "studio.log"

val initial: Upd<AppState, AppMessage, AppDeps> =
    AppState() with Effekt.single<AppDeps, AppMessage> { LoadProjects }

fun update(msg: AppMessage, state: AppState): Upd<AppState, AppMessage, AppDeps> = when (msg) {
    is LoadProjects -> state with Effekt.single { deps -> ProjectsLoaded(deps.projects.list()) }

    is ProjectsLoaded -> state.copy(projects = msg.projects) with
        msg.projects.mapTo(mutableSetOf()) { p ->
            Effekt.single { deps -> RunsLoaded(p.id, deps.runs.listForProject(p.id)) }
        }

    is RunsLoaded -> state.copy(runsByProject = state.runsByProject + (msg.projectId to msg.runs)) with noEffects()

    is AddProject -> state with Effekt.single { deps ->
        val p = deps.projects.add(msg.folder.name, msg.folder.absolutePath)
        ProjectAdded(p)
    }

    is ProjectAdded -> state.copy(
        projects = (state.projects + msg.project).sortedBy { it.name.lowercase() },
        selectedProjectId = msg.project.id,
    ) with noEffects()

    is RemoveProject -> removeProject(msg, state)
    is ProjectRemoved -> state.copy(
        projects = state.projects.filterNot { it.id == msg.projectId },
        tabsByProject = state.tabsByProject - msg.projectId,
        selectedTabByProject = state.selectedTabByProject - msg.projectId,
        expandedProjects = state.expandedProjects - msg.projectId,
        runsByProject = state.runsByProject - msg.projectId,
        selectedProjectId = if (state.selectedProjectId == msg.projectId) null else state.selectedProjectId,
    ) with noEffects()

    is SelectProject -> state.copy(selectedProjectId = msg.projectId) with noEffects()

    is ToggleProjectExpanded -> state.copy(
        expandedProjects = state.expandedProjects.toggle(msg.projectId),
    ) with noEffects()

    is NewTab -> {
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

    is SelectTab -> state.copy(
        selectedTabByProject = state.selectedTabByProject + (msg.projectId to msg.tabId),
    ) with noEffects()

    is CloseTab -> closeTab(msg, state)

    is SelectSection -> state.updateTab(msg.projectId, msg.tabId) {
        it.copy(section = msg.section)
    } with noEffects()

    is UpdateConfig -> state.updateTab(msg.projectId, msg.tabId) {
        it.copy(config = msg.config)
    } with noEffects()

    is OpenRunInTab -> openRunInTab(msg, state)

    is RunTabRestored -> state.copy(
        tabsByProject = state.tabsByProject.appendTab(msg.projectId, msg.tab),
        selectedProjectId = msg.projectId,
        selectedTabByProject = state.selectedTabByProject + (msg.projectId to msg.tab.id),
    ) with noEffects()

    is StartRun -> startRun(msg, state)

    is RunStarted -> state.updateTab(msg.projectId, msg.tabId) {
        it.copy(
            status = TabStatus.Running,
            section = TabSection.Console,
            runId = msg.runId,
            outputName = msg.outputName,
            outputDir = msg.outputDir,
        )
    } with noEffects()

    is RunSpawnFailed -> state.updateTab(msg.projectId, msg.tabId) {
        it.copy(status = TabStatus.Failure)
    } with Effekt.single { deps ->
        RunsLoaded(msg.projectId, deps.runs.listForProject(msg.projectId))
    }

    is RunCompleted -> runCompleted(msg, state)

    is CancelRun -> cancelRun(msg, state)
}

private fun startRun(msg: StartRun, state: AppState): Upd<AppState, AppMessage, AppDeps> {
    val tab = state.tabOf(msg.projectId, msg.tabId) ?: return state with noEffects()
    val project = state.projectOf(msg.projectId) ?: return state with noEffects()
    if (tab.status == TabStatus.Running) return state with noEffects()

    return state with Effekt.flow<AppDeps, AppMessage> { deps ->
        flow {
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

            emit(RunStarted(project.id, msg.tabId, run.id, outputName, outputDir))

            val process = try {
                ProfilerProcess.spawn(File(project.path), outputDir, scenarioFile, tab.config)
            } catch (e: Exception) {
                console.append("Failed to start profiler: ${e.message}")
                deps.runs.finish(run.id, RunStatus.Failure, null)
                emit(RunSpawnFailed(project.id, msg.tabId, run.id))
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
            emit(RunCompleted(project.id, msg.tabId, exit, cancelled))
        }
    }
}

private fun runCompleted(msg: RunCompleted, state: AppState): Upd<AppState, AppMessage, AppDeps> {
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
    return state.updateTab(msg.projectId, msg.tabId) {
        it.copy(status = newTabStatus)
    } with Effekt.single { deps ->
        runId?.let { deps.runs.finish(it, runStatus, msg.exit) }
        RunsLoaded(msg.projectId, deps.runs.listForProject(msg.projectId))
    }
}

private fun cancelRun(msg: CancelRun, state: AppState): Upd<AppState, AppMessage, AppDeps> {
    val tab = state.tabOf(msg.projectId, msg.tabId) ?: return state with noEffects()
    val project = state.projectOf(msg.projectId) ?: return state with noEffects()
    val newState = if (tab.status == TabStatus.Running) {
        state.updateTab(msg.projectId, msg.tabId) { it.copy(status = TabStatus.Cancelled) }
    } else state

    return newState with Effekt.idle<AppDeps, AppMessage> { deps ->
        deps.processes.cancel(msg.tabId)
        val console = deps.consoles.get(msg.tabId)
        GradleDaemonControl.stopDaemons(File(project.path), tab.config.gradleUserHome) {
            console.append(it)
        }
    }
}

private fun closeTab(msg: CloseTab, state: AppState): Upd<AppState, AppMessage, AppDeps> {
    val tabs = state.tabsByProject[msg.projectId].orEmpty()
    val tab = tabs.firstOrNull { it.id == msg.tabId }
    val wasRunning = tab?.status == TabStatus.Running
    val project = state.projectOf(msg.projectId)

    val remaining = tabs.filterNot { it.id == msg.tabId }
    val sel = state.selectedTabByProject
    val newSel = when {
        sel[msg.projectId] != msg.tabId -> sel
        remaining.isEmpty() -> sel - msg.projectId
        else -> sel + (msg.projectId to remaining.last().id)
    }

    val effects = mutableSetOf<Effekt<AppDeps, AppMessage>>()
    if (wasRunning && project != null) {
        effects.add(Effekt.idle { deps ->
            deps.processes.cancel(msg.tabId)
            val console = deps.consoles.get(msg.tabId)
            GradleDaemonControl.stopDaemons(File(project.path), tab!!.config.gradleUserHome) {
                console.append(it)
            }
        })
    }
    effects.add(Effekt.idle { deps ->
        deps.processes.remove(msg.tabId)
        deps.consoles.remove(msg.tabId)
    })

    return state.copy(
        tabsByProject = state.tabsByProject + (msg.projectId to remaining),
        selectedTabByProject = newSel,
    ) with effects
}

private fun removeProject(msg: RemoveProject, state: AppState): Upd<AppState, AppMessage, AppDeps> =
    state with Effekt.single { deps ->
        state.tabsByProject[msg.projectId].orEmpty().forEach { tab ->
            deps.processes.cancel(tab.id)
            deps.processes.remove(tab.id)
            deps.consoles.remove(tab.id)
        }
        deps.runs.deleteForProject(msg.projectId)
        deps.projects.remove(msg.projectId)
        val projectRunsDir = AppPaths.runsDir.resolve(msg.projectId.toString())
        if (projectRunsDir.exists()) projectRunsDir.deleteRecursively()
        ProjectRemoved(msg.projectId)
    }

private fun openRunInTab(msg: OpenRunInTab, state: AppState): Upd<AppState, AppMessage, AppDeps> {
    val existing = state.tabsByProject[msg.projectId]?.firstOrNull { it.runId == msg.runId }
    if (existing != null) {
        return state.copy(
            selectedProjectId = msg.projectId,
            selectedTabByProject = state.selectedTabByProject + (msg.projectId to existing.id),
        ) with noEffects()
    }
    return state with Effekt.single<AppDeps, AppMessage> { deps ->
        val run = deps.runs.findById(msg.runId) ?: return@single LoadProjects // no-op fallback
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
        RunTabRestored(msg.projectId, tab)
    }
}

private fun Set<Int>.toggle(value: Int): Set<Int> =
    if (value in this) this - value else this + value

private fun Map<Int, List<TabState>>.appendTab(projectId: Int, tab: TabState): Map<Int, List<TabState>> =
    this + (projectId to ((this[projectId] ?: emptyList()) + tab))

private fun AppState.updateTab(
    projectId: Int,
    tabId: Long,
    transform: (TabState) -> TabState,
): AppState {
    val list = tabsByProject[projectId] ?: return this
    return copy(tabsByProject = tabsByProject + (projectId to list.map {
        if (it.id == tabId) transform(it) else it
    }))
}

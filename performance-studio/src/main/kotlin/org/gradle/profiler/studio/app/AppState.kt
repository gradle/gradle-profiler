package org.gradle.profiler.studio.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gradle.profiler.studio.data.AppPaths
import org.gradle.profiler.studio.data.Project
import org.gradle.profiler.studio.data.ProjectRepository
import org.gradle.profiler.studio.data.ProjectStatus
import org.gradle.profiler.studio.data.RunRepository
import org.gradle.profiler.studio.data.RunStatus
import org.gradle.profiler.studio.domain.ConfigDraft
import org.gradle.profiler.studio.domain.TabSection
import org.gradle.profiler.studio.domain.TabState
import org.gradle.profiler.studio.domain.TabStatus
import org.gradle.profiler.studio.runner.ConsoleBuffer
import org.gradle.profiler.studio.runner.GradleDaemonControl
import org.gradle.profiler.studio.runner.HoconWriter
import org.gradle.profiler.studio.runner.ProfilerProcess
import java.io.File

class AppState(
    private val projectRepo: ProjectRepository,
    private val runRepo: RunRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val projects: StateFlow<List<Project>> = projectRepo.projects

    private val _selectedProjectId = MutableStateFlow<Int?>(null)
    val selectedProjectId: StateFlow<Int?> = _selectedProjectId.asStateFlow()

    private val _tabsByProject = MutableStateFlow<Map<Int, List<TabState>>>(emptyMap())
    val tabsByProject: StateFlow<Map<Int, List<TabState>>> = _tabsByProject.asStateFlow()

    private val _selectedTabByProject = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val selectedTabByProject: StateFlow<Map<Int, Long>> = _selectedTabByProject.asStateFlow()

    val projectStatuses: StateFlow<Map<Int, ProjectStatus>> =
        combine(projects, _tabsByProject) { ps, tabs ->
            ps.associate { p ->
                val pTabs = tabs[p.id].orEmpty()
                p.id to when {
                    pTabs.any { it.status == TabStatus.Running } -> ProjectStatus.Running
                    pTabs.any { it.status == TabStatus.Failure } -> ProjectStatus.Failure
                    pTabs.any { it.status == TabStatus.Success } -> ProjectStatus.Success
                    else -> ProjectStatus.Idle
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val consoles = mutableMapOf<Long, ConsoleBuffer>()
    private val processes = mutableMapOf<Long, ProfilerProcess>()

    fun selectProject(id: Int) { _selectedProjectId.value = id }

    fun addProject(folder: File) {
        val project = projectRepo.add(folder.name, folder.absolutePath)
        _selectedProjectId.value = project.id
    }

    fun newTab(projectId: Int): TabState {
        val tab = TabState(
            id = System.nanoTime(),
            status = TabStatus.Editing,
            config = ConfigDraft(),
        )
        _tabsByProject.update { it + (projectId to ((it[projectId] ?: emptyList()) + tab)) }
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
        consoles.remove(tabId)
    }

    fun selectSection(projectId: Int, tabId: Long, section: TabSection) {
        mutateTab(projectId, tabId) { it.copy(section = section) }
    }

    fun updateConfig(projectId: Int, tabId: Long, transform: (ConfigDraft) -> ConfigDraft) {
        mutateTab(projectId, tabId) { it.copy(config = transform(it.config)) }
    }

    fun consoleFor(tabId: Long): ConsoleBuffer = consoles.getOrPut(tabId) { ConsoleBuffer() }

    fun startRun(project: Project, tabId: Long) {
        val tab = _tabsByProject.value[project.id]?.firstOrNull { it.id == tabId } ?: return
        if (tab.status == TabStatus.Running) return

        val outputName = runRepo.nextOutputName(project.id)
        val outputDir = AppPaths.runsDir.resolve(project.id.toString()).resolve(outputName).apply { mkdirs() }
        val scenarioFile = outputDir.resolve("scenario.conf")
        HoconWriter.write(tab.config, scenarioFile)

        val run = runRepo.create(project.id, outputName, outputDir.absolutePath)
        val console = consoleFor(tabId)
        console.clear()

        mutateTab(project.id, tabId) {
            it.copy(
                status = TabStatus.Running,
                section = TabSection.Console,
                runId = run.id,
                outputName = outputName,
                outputDir = outputDir,
            )
        }

        scope.launch {
            val process = try {
                ProfilerProcess.spawn(File(project.path), outputDir, scenarioFile, tab.config)
            } catch (e: Exception) {
                console.append("Failed to start profiler: ${e.message}")
                runRepo.finish(run.id, RunStatus.Failure, null)
                mutateTab(project.id, tabId) { it.copy(status = TabStatus.Failure) }
                return@launch
            }
            processes[tabId] = process

            launch { process.streamOutput { console.append(it) } }
            val exit = process.awaitExit()
            processes.remove(tabId)

            val current = _tabsByProject.value[project.id]?.firstOrNull { it.id == tabId }?.status
            val (runStatus, tabStatus) = when {
                current == TabStatus.Cancelled -> RunStatus.Cancelled to TabStatus.Cancelled
                exit == 0 -> RunStatus.Success to TabStatus.Success
                else -> RunStatus.Failure to TabStatus.Failure
            }
            runRepo.finish(run.id, runStatus, exit)
            mutateTab(project.id, tabId) { it.copy(status = tabStatus) }
        }
    }

    fun cancelRun(projectId: Int, tabId: Long) {
        processes[tabId]?.cancel()
        mutateTab(projectId, tabId) {
            if (it.status == TabStatus.Running) it.copy(status = TabStatus.Cancelled) else it
        }
        val project = projects.value.firstOrNull { it.id == projectId } ?: return
        val tab = _tabsByProject.value[projectId]?.firstOrNull { it.id == tabId } ?: return
        val console = consoleFor(tabId)
        scope.launch {
            GradleDaemonControl.stopDaemons(File(project.path), tab.config.gradleUserHome) {
                console.append(it)
            }
        }
    }

    private fun mutateTab(projectId: Int, tabId: Long, transform: (TabState) -> TabState) {
        _tabsByProject.update { map ->
            val list = (map[projectId] ?: return@update map).map { if (it.id == tabId) transform(it) else it }
            map + (projectId to list)
        }
    }
}

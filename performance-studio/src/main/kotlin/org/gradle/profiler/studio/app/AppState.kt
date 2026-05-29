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
import org.gradle.profiler.studio.data.Run
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

private const val STUDIO_LOG = "studio.log"

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

    private val _expandedProjects = MutableStateFlow<Set<Int>>(emptySet())
    val expandedProjects: StateFlow<Set<Int>> = _expandedProjects.asStateFlow()

    private val _runsByProject = MutableStateFlow<Map<Int, List<Run>>>(emptyMap())
    val runsByProject: StateFlow<Map<Int, List<Run>>> = _runsByProject.asStateFlow()

    val projectStatuses: StateFlow<Map<Int, ProjectStatus>> =
        combine(projects, _tabsByProject, _runsByProject) { ps, tabs, runs ->
            ps.associate { p ->
                val pTabs = tabs[p.id].orEmpty()
                val latestRun = runs[p.id]?.firstOrNull()
                p.id to when {
                    pTabs.any { it.status == TabStatus.Running } -> ProjectStatus.Running
                    pTabs.any { it.status == TabStatus.Failure } -> ProjectStatus.Failure
                    pTabs.any { it.status == TabStatus.Success } -> ProjectStatus.Success
                    latestRun?.status == RunStatus.Failure -> ProjectStatus.Failure
                    latestRun?.status == RunStatus.Success -> ProjectStatus.Success
                    else -> ProjectStatus.Idle
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val consoles = mutableMapOf<Long, ConsoleBuffer>()
    private val processes = mutableMapOf<Long, ProfilerProcess>()

    init {
        scope.launch {
            projects.collect { ps -> refreshRunsFor(ps.map { it.id }) }
        }
    }

    fun selectProject(id: Int) { _selectedProjectId.value = id }

    fun addProject(folder: File) {
        val project = projectRepo.add(folder.name, folder.absolutePath)
        _selectedProjectId.value = project.id
    }

    fun toggleProjectExpanded(projectId: Int) {
        _expandedProjects.update { if (projectId in it) it - projectId else it + projectId }
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
        val wasRunning = _tabsByProject.value[projectId]?.firstOrNull { it.id == tabId }?.status == TabStatus.Running
        if (wasRunning) cancelRun(projectId, tabId)
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

    fun openRunInTab(projectId: Int, runId: Int) {
        val existing = _tabsByProject.value[projectId]?.firstOrNull { it.runId == runId }
        if (existing != null) {
            _selectedProjectId.value = projectId
            _selectedTabByProject.update { it + (projectId to existing.id) }
            return
        }
        val run = runRepo.findById(runId) ?: return
        val tabStatus = when (run.status) {
            RunStatus.Running -> TabStatus.Editing // stale running record from a previous crash
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
        _tabsByProject.update { it + (projectId to ((it[projectId] ?: emptyList()) + tab)) }
        _selectedProjectId.value = projectId
        _selectedTabByProject.update { it + (projectId to tab.id) }
        val buffer = consoleFor(tab.id)
        val logFile = File(run.outputDir).resolve(STUDIO_LOG)
        if (logFile.exists()) {
            buffer.loadAll(logFile.readLines())
        }
    }

    fun startRun(project: Project, tabId: Long) {
        val tab = _tabsByProject.value[project.id]?.firstOrNull { it.id == tabId } ?: return
        if (tab.status == TabStatus.Running) return

        val outputName = runRepo.nextOutputName(project.id)
        val outputDir = AppPaths.runsDir.resolve(project.id.toString()).resolve(outputName).apply { mkdirs() }
        val scenarioFile = outputDir.resolve("scenario.conf")
        HoconWriter.write(tab.config, scenarioFile)

        val run = runRepo.create(project.id, outputName, outputDir.absolutePath, tab.config)
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
                refreshRunsFor(listOf(project.id))
                return@launch
            }
            processes[tabId] = process

            val logFile = outputDir.resolve(STUDIO_LOG)
            launch {
                logFile.bufferedWriter().use { writer ->
                    process.streamOutput { line ->
                        console.append(line)
                        writer.appendLine(line)
                    }
                }
            }
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
            refreshRunsFor(listOf(project.id))
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

    private fun refreshRunsFor(projectIds: List<Int>) {
        if (projectIds.isEmpty()) return
        val updated = projectIds.associateWith { runRepo.listForProject(it) }
        _runsByProject.update { it + updated }
    }

    private fun mutateTab(projectId: Int, tabId: Long, transform: (TabState) -> TabState) {
        _tabsByProject.update { map ->
            val list = (map[projectId] ?: return@update map).map { if (it.id == tabId) transform(it) else it }
            map + (projectId to list)
        }
    }
}

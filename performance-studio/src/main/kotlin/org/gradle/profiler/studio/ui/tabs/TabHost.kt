package org.gradle.profiler.studio.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.gradle.profiler.studio.app.AppState
import org.gradle.profiler.studio.data.Project
import org.gradle.profiler.studio.domain.TabStatus

@Composable
fun TabHost(appState: AppState, project: Project, modifier: Modifier = Modifier) {
    val tabsMap by appState.tabsByProject.collectAsState()
    val selectedMap by appState.selectedTabByProject.collectAsState()

    val tabs = tabsMap[project.id].orEmpty()
    val selectedId = selectedMap[project.id]

    LaunchedEffect(project.id, tabs.isEmpty()) {
        if (tabs.isEmpty()) appState.newTab(project.id)
    }

    Column(modifier.fillMaxSize()) {
        TabStrip(
            tabs = tabs,
            selectedId = selectedId,
            onSelect = { appState.selectTab(project.id, it) },
            onClose = { appState.closeTab(project.id, it) },
            onNew = { appState.newTab(project.id) },
        )
        val current = tabs.firstOrNull { it.id == selectedId } ?: return
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ConfigSection(
                config = current.config,
                readOnly = current.status != TabStatus.Editing,
                onChange = { newConfig ->
                    appState.updateConfig(project.id, current.id) { newConfig }
                },
                onRun = { appState.startRun(project, current.id) },
            )
            if (current.status == TabStatus.Running) {
                ConsoleSection(
                    buffer = appState.consoleFor(current.id),
                    status = current.status,
                    onCancel = { appState.cancelRun(project.id, current.id) },
                )
            }
        }
    }
}

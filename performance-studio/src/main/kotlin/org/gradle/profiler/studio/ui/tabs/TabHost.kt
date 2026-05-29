package org.gradle.profiler.studio.ui.tabs

import androidx.compose.foundation.layout.Box
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
import org.gradle.profiler.studio.domain.TabSection
import org.gradle.profiler.studio.domain.TabState
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
        val visibleSections = if (current.outputDir != null) TabSection.entries else listOf(TabSection.Scenario)
        val effectiveSection = if (current.section in visibleSections) current.section else TabSection.Scenario
        SubTabBar(
            sections = visibleSections,
            selected = effectiveSection,
            onSelect = { appState.selectSection(project.id, current.id, it) },
        )
        Box(Modifier.fillMaxSize()) {
            SectionContent(appState, project, current.copy(section = effectiveSection))
        }
    }
}

@Composable
private fun SectionContent(appState: AppState, project: Project, tab: TabState) {
    when (tab.section) {
        TabSection.Scenario -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ConfigSection(
                config = tab.config,
                readOnly = tab.status == TabStatus.Running,
                onChange = { newConfig -> appState.updateConfig(project.id, tab.id) { newConfig } },
                onRun = { appState.startRun(project, tab.id) },
            )
        }
        TabSection.Console -> ConsoleSection(
            buffer = appState.consoleFor(tab.id),
            status = tab.status,
            onCancel = { appState.cancelRun(project.id, tab.id) },
            modifier = Modifier.fillMaxSize(),
        )
        TabSection.Results -> ResultsSection(
            outputDir = tab.outputDir,
            status = tab.status,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

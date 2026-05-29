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
import org.gradle.profiler.studio.app.AppController
import org.gradle.profiler.studio.app.AppMessage
import org.gradle.profiler.studio.data.Project
import org.gradle.profiler.studio.domain.TabSection
import org.gradle.profiler.studio.domain.TabState
import org.gradle.profiler.studio.domain.TabStatus
import java.io.File

@Composable
fun TabHost(controller: AppController, project: Project, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val tabs = state.tabsByProject[project.id].orEmpty()
    val selectedId = state.selectedTabByProject[project.id]

    LaunchedEffect(project.id, tabs.isEmpty()) {
        if (tabs.isEmpty()) controller.dispatch(AppMessage.NewTab(project.id))
    }

    Column(modifier.fillMaxSize()) {
        TabStrip(
            tabs = tabs,
            selectedId = selectedId,
            onSelect = { controller.dispatch(AppMessage.SelectTab(project.id, it)) },
            onClose = { controller.dispatch(AppMessage.CloseTab(project.id, it)) },
            onNew = { controller.dispatch(AppMessage.NewTab(project.id)) },
        )
        val current = tabs.firstOrNull { it.id == selectedId } ?: return
        val visibleSections =
            if (current.outputDir != null) TabSection.entries else listOf(TabSection.Scenario)
        val effectiveSection =
            if (current.section in visibleSections) current.section else TabSection.Scenario
        SubTabBar(
            sections = visibleSections,
            selected = effectiveSection,
            onSelect = { controller.dispatch(AppMessage.SelectSection(project.id, current.id, it)) },
        )
        Box(Modifier.fillMaxSize()) {
            SectionContent(controller, project, current.copy(section = effectiveSection))
        }
    }
}

@Composable
private fun SectionContent(controller: AppController, project: Project, tab: TabState) {
    when (tab.section) {
        TabSection.Scenario -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ConfigSection(
                config = tab.config,
                readOnly = tab.status == TabStatus.Running,
                projectDir = File(project.path),
                onChange = { newConfig ->
                    controller.dispatch(AppMessage.UpdateConfig(project.id, tab.id, newConfig))
                },
                onRun = { controller.dispatch(AppMessage.StartRun(project.id, tab.id)) },
            )
        }
        TabSection.Console -> ConsoleSection(
            buffer = controller.consoleFor(tab.id),
            status = tab.status,
            onCancel = { controller.dispatch(AppMessage.CancelRun(project.id, tab.id)) },
            modifier = Modifier.fillMaxSize(),
        )
        TabSection.Results -> ResultsSection(
            outputDir = tab.outputDir,
            status = tab.status,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

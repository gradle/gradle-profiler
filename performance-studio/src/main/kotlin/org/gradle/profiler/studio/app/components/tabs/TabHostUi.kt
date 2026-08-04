package org.gradle.profiler.studio.app.components.tabs

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
import org.gradle.profiler.studio.data.Project
import org.gradle.profiler.studio.domain.TabSection
import org.gradle.profiler.studio.domain.TabState
import org.gradle.profiler.studio.domain.TabStatus
import java.io.File

@Composable
fun TabHostUi(
    component: TabHostComponent,
    project: Project,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()
    val tabs = state.tabsByProject[project.id].orEmpty()
    val selectedId = state.selectedTabByProject[project.id]

    LaunchedEffect(project.id, tabs.isEmpty()) {
        if (tabs.isEmpty()) component.dispatch(TabHostMessage.NewTab(project.id))
    }

    Column(modifier.fillMaxSize()) {
        TabStrip(
            tabs = tabs,
            selectedId = selectedId,
            onSelect = { component.dispatch(TabHostMessage.SelectTab(project.id, it)) },
            onClose = { component.dispatch(TabHostMessage.CloseTab(project.id, it)) },
            onNew = { component.dispatch(TabHostMessage.NewTab(project.id)) },
        )
        val current = tabs.firstOrNull { it.id == selectedId } ?: return
        val visibleSections =
            if (current.outputDir != null) TabSection.entries else listOf(TabSection.Scenario)
        val effectiveSection =
            if (current.section in visibleSections) current.section else TabSection.Scenario
        SubTabBar(
            sections = visibleSections,
            selected = effectiveSection,
            onSelect = { component.dispatch(TabHostMessage.SelectSection(project.id, current.id, it)) },
        )
        Box(Modifier.fillMaxSize()) {
            SectionContent(component, project, current.copy(section = effectiveSection))
        }
    }
}

@Composable
private fun SectionContent(component: TabHostComponent, project: Project, tab: TabState) {
    when (tab.section) {
        TabSection.Scenario -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ConfigSection(
                config = tab.config,
                readOnly = tab.status == TabStatus.Running,
                projectDir = File(project.path),
                onChange = { newConfig ->
                    component.dispatch(TabHostMessage.UpdateConfig(project.id, tab.id, newConfig))
                },
                onRun = { component.dispatch(TabHostMessage.StartRun(project.id, tab.id)) },
            )
        }
        TabSection.Console -> ConsoleSection(
            buffer = component.consoleFor(tab.id),
            status = tab.status,
            onCancel = { component.dispatch(TabHostMessage.CancelRun(project.id, tab.id)) },
            modifier = Modifier.fillMaxSize(),
        )
        TabSection.Results -> ResultsSection(
            outputDir = tab.outputDir,
            status = tab.status,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

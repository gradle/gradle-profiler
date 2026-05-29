package org.gradle.profiler.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.gradle.profiler.studio.app.AppState
import org.gradle.profiler.studio.data.ProjectRepository
import org.gradle.profiler.studio.data.RunRepository
import org.gradle.profiler.studio.data.db.StudioDatabase
import org.gradle.profiler.studio.ui.FolderPicker
import org.gradle.profiler.studio.ui.sidebar.ProjectList
import org.gradle.profiler.studio.ui.tabs.TabHost
import org.gradle.profiler.studio.ui.theme.StudioColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text

fun main() = application {
    val initResult = remember { runCatching(::initAppState) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Gradle Performance Studio",
    ) {
        IntUiTheme(isDark = isSystemInDarkTheme()) {
            Box(Modifier.fillMaxSize().background(StudioColors.windowBg)) {
                initResult.fold(
                    onSuccess = { StudioRoot(it) },
                    onFailure = { FatalErrorScreen(it) },
                )
            }
        }
    }
}

private fun initAppState(): AppState {
    val db = StudioDatabase.connect()
    return AppState(ProjectRepository(db), RunRepository(db))
}

@Composable
private fun StudioRoot(appState: AppState) {
    Row(Modifier.fillMaxSize()) {
        ProjectList(
            appState = appState,
            onAddProject = {
                FolderPicker.pick()?.let(appState::addProject)
            },
        )
        MainPane(appState)
    }
}

@Composable
private fun MainPane(appState: AppState) {
    val projects by appState.projects.collectAsState()
    val selectedId by appState.selectedProjectId.collectAsState()
    val selected = projects.firstOrNull { it.id == selectedId }

    when {
        projects.isEmpty() -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No projects yet", style = JewelTheme.defaultTextStyle)
                Text(
                    "Click + in the sidebar to add one.",
                    style = JewelTheme.defaultTextStyle.copy(color = StudioColors.mutedText),
                )
            }
        }
        selected == null -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("Select a project", style = JewelTheme.defaultTextStyle.copy(color = StudioColors.mutedText))
        }
        else -> TabHost(appState, selected)
    }
}

@Composable
private fun FatalErrorScreen(error: Throwable) {
    Column(
        Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Studio failed to start",
            style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            error.message ?: error::class.qualifiedName.orEmpty(),
            style = JewelTheme.defaultTextStyle.copy(color = StudioColors.mutedText),
        )
        Text(
            error.stackTraceToString(),
            style = JewelTheme.defaultTextStyle.copy(color = StudioColors.mutedText),
        )
    }
}

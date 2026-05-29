package org.gradle.profiler.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.gradle.profiler.studio.app.AppState
import org.gradle.profiler.studio.data.ProjectRepository
import org.gradle.profiler.studio.data.db.StudioDatabase
import org.gradle.profiler.studio.ui.FolderPicker
import org.gradle.profiler.studio.ui.sidebar.ProjectList
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text

fun main() = application {
    val appState = remember { initAppState() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Gradle Performance Studio",
    ) {
        IntUiTheme(isDark = false) {
            StudioRoot(appState)
        }
    }
}

private fun initAppState(): AppState {
    val db = StudioDatabase.connect()
    val repo = ProjectRepository(db)
    return AppState(repo)
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

    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            projects.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No projects yet", style = JewelTheme.defaultTextStyle)
                Text("Click + in the sidebar to add one.", style = JewelTheme.defaultTextStyle)
            }
            selected == null -> Text("Select a project", style = JewelTheme.defaultTextStyle)
            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(selected.name, style = JewelTheme.defaultTextStyle)
                Text(selected.path, style = JewelTheme.defaultTextStyle)
            }
        }
    }
}

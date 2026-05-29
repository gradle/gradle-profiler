package org.gradle.profiler.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.gradle.profiler.studio.app.AppController
import org.gradle.profiler.studio.app.AppDeps
import org.gradle.profiler.studio.app.AppMessage
import org.gradle.profiler.studio.app.ConsoleRegistry
import org.gradle.profiler.studio.app.ProcessRegistry
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
import java.awt.Cursor

fun main() = application {
    val initResult = remember { runCatching(::initController) }
    Window(
        onCloseRequest = {
            initResult.getOrNull()?.close()
            exitApplication()
        },
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

private fun initController(): AppController {
    val db = StudioDatabase.connect()
    val deps = AppDeps(
        projects = ProjectRepository(db),
        runs = RunRepository(db),
        consoles = ConsoleRegistry(),
        processes = ProcessRegistry(),
    )
    return AppController(deps)
}

private val MIN_SIDEBAR_WIDTH = 160.dp
private val MAX_SIDEBAR_WIDTH = 480.dp

@Composable
private fun StudioRoot(controller: AppController) {
    var sidebarWidth by remember { mutableStateOf(220.dp) }
    val density = LocalDensity.current
    Row(Modifier.fillMaxSize()) {
        ProjectList(
            controller = controller,
            onAddProject = { FolderPicker.pick()?.let { controller.dispatch(AppMessage.AddProject(it)) } },
            modifier = Modifier.width(sidebarWidth),
        )
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(StudioColors.divider)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { deltaPx ->
                        val deltaDp: Dp = with(density) { deltaPx.toDp() }
                        sidebarWidth = (sidebarWidth + deltaDp).coerceIn(MIN_SIDEBAR_WIDTH, MAX_SIDEBAR_WIDTH)
                    },
                ),
        )
        MainPane(controller)
    }
}

@Composable
private fun MainPane(controller: AppController) {
    val state by controller.state.collectAsState()
    val selected = state.selectedProjectId?.let { id -> state.projects.firstOrNull { it.id == id } }

    when {
        state.projects.isEmpty() -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
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
        else -> TabHost(controller, selected)
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

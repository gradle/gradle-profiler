package org.gradle.profiler.studio.app.components.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gradle.profiler.studio.data.Project
import org.gradle.profiler.studio.data.ProjectStatus
import org.gradle.profiler.studio.data.Run
import org.gradle.profiler.studio.data.RunStatus
import org.gradle.profiler.studio.ui.components.ConfirmDialog
import org.gradle.profiler.studio.ui.theme.StudioColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun SidebarUi(
    component: SidebarComponent,
    runningProjects: Set<Int>,
    onAddProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()
    var pendingRemove by remember { mutableStateOf<Project?>(null) }

    Column(
        modifier
            .fillMaxHeight()
            .background(StudioColors.sidebarBg)
            .padding(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "PROJECTS",
                style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddProject) {
                Text("+", style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Bold))
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.projects.forEach { project ->
                val runs = state.runsByProject[project.id].orEmpty()
                val hasRuns = runs.isNotEmpty()
                val isExpanded = hasRuns && project.id in state.expandedProjects
                val status = projectStatus(project.id, runningProjects, runs)
                item(key = "p-${project.id}") {
                    ProjectRow(
                        project = project,
                        status = status,
                        selected = project.id == state.selectedProjectId,
                        expanded = isExpanded,
                        hasRuns = hasRuns,
                        onClick = { component.dispatch(SidebarMessage.SelectProject(project.id)) },
                        onToggleExpand = { component.dispatch(SidebarMessage.ToggleExpand(project.id)) },
                        onRemove = { pendingRemove = project },
                    )
                }
                if (isExpanded) {
                    items(runs.size, key = { "r-${runs[it].id}" }) { i ->
                        RunRow(
                            run = runs[i],
                            onOpen = {
                                component.dispatch(SidebarMessage.OpenRunRequested(project.id, runs[i].id))
                            },
                        )
                    }
                }
            }
        }
    }

    pendingRemove?.let { project ->
        val runCount = state.runsByProject[project.id]?.size ?: 0
        ConfirmDialog(
            title = "Remove project?",
            message = buildString {
                append("Remove “${project.name}” from Gradle Performance Studio? ")
                if (runCount > 0) append("Its $runCount run(s) and run output folder will be deleted from disk.")
                else append("No saved runs will be affected.")
                appendLine()
                appendLine()
                append("The project folder on disk (${project.path}) is not touched.")
            },
            confirmLabel = "Remove",
            onConfirm = { component.dispatch(SidebarMessage.RemoveProjectRequested(project.id)) },
            onDismiss = { pendingRemove = null },
        )
    }
}

private fun projectStatus(projectId: Int, running: Set<Int>, runs: List<Run>): ProjectStatus = when {
    projectId in running -> ProjectStatus.Running
    runs.firstOrNull()?.status == RunStatus.Failure -> ProjectStatus.Failure
    runs.firstOrNull()?.status == RunStatus.Success -> ProjectStatus.Success
    else -> ProjectStatus.Idle
}

@Composable
private fun ProjectRow(
    project: Project,
    status: ProjectStatus,
    selected: Boolean,
    expanded: Boolean,
    hasRuns: Boolean,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
) {
    val rowBg = if (selected) StudioColors.selectedRowBg else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasRuns) {
            Text(
                if (expanded) "▾" else "▸",
                style = JewelTheme.defaultTextStyle.copy(color = StudioColors.mutedText),
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 4.dp),
            )
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(20.dp))
        }
        StatusDot(status)
        Spacer(Modifier.width(8.dp))
        Text(project.name, style = JewelTheme.defaultTextStyle, modifier = Modifier.weight(1f))
        Text(
            "×",
            style = JewelTheme.defaultTextStyle.copy(
                color = StudioColors.mutedText,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                .clickable(onClick = onRemove)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun RunRow(run: Run, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .clickable(onClick = onOpen)
            .padding(start = 28.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RunGlyph(run.status)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(run.outputName, style = JewelTheme.defaultTextStyle)
            Text(
                timeFmt.format(run.startedAt),
                style = JewelTheme.defaultTextStyle.copy(color = StudioColors.mutedText),
            )
        }
    }
}

@Composable
private fun StatusDot(status: ProjectStatus) {
    val color = when (status) {
        ProjectStatus.Idle -> Color(0xFF9E9E9E)
        ProjectStatus.Running -> Color(0xFF2196F3)
        ProjectStatus.Success -> Color(0xFF4CAF50)
        ProjectStatus.Failure -> Color(0xFFF44336)
    }
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun RunGlyph(status: RunStatus) {
    val (text, color) = when (status) {
        RunStatus.Running -> "●" to Color(0xFF2196F3)
        RunStatus.Success -> "✓" to Color(0xFF4CAF50)
        RunStatus.Failure -> "✗" to Color(0xFFF44336)
        RunStatus.Cancelled -> "◐" to Color(0xFF9E9E9E)
    }
    Text(text, style = JewelTheme.defaultTextStyle.copy(color = color))
}

package org.gradle.profiler.studio.ui.sidebar

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gradle.profiler.studio.app.AppState
import org.gradle.profiler.studio.data.Project
import org.gradle.profiler.studio.data.ProjectStatus
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text

@Composable
fun ProjectList(
    appState: AppState,
    onAddProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val projects by appState.projects.collectAsState()
    val selectedId by appState.selectedProjectId.collectAsState()
    val statuses by appState.projectStatuses.collectAsState()

    Column(
        modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(Color(0xFFF2F2F2))
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
            items(projects, key = { it.id }) { project ->
                ProjectRow(
                    project = project,
                    status = statuses[project.id] ?: ProjectStatus.Idle,
                    selected = project.id == selectedId,
                    onClick = { appState.selectProject(project.id) },
                )
            }
        }
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    status: ProjectStatus,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val rowBg = if (selected) Color(0xFFD7E4F2) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status)
        Spacer(Modifier.width(8.dp))
        Text(project.name, style = JewelTheme.defaultTextStyle)
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

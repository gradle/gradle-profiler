package org.gradle.profiler.studio.app.components.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gradle.profiler.studio.domain.TabStatus
import org.gradle.profiler.studio.runner.ConsoleBuffer
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

private val consoleStyle = TextStyle(
    color = Color(0xFFE0E0E0),
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
)

@Composable
fun ConsoleSection(
    buffer: ConsoleBuffer,
    status: TabStatus,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines by buffer.lines.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (lines.isEmpty()) "Console (empty)" else "Console (${lines.size} lines)",
                style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            if (status == TabStatus.Running) {
                DefaultButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .padding(8.dp),
        ) {
            items(lines) { Text(it, style = consoleStyle) }
        }
    }
}

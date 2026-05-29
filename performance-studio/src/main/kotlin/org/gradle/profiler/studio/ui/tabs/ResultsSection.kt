package org.gradle.profiler.studio.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import org.gradle.profiler.studio.domain.TabStatus
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import java.awt.Desktop
import java.io.File

@Composable
fun ResultsSection(
    outputDir: File?,
    status: TabStatus,
    modifier: Modifier = Modifier,
) {
    if (outputDir == null) {
        EmptyState("No results yet — run the scenario to see output files.", modifier)
        return
    }

    var files by remember(outputDir.absolutePath) { mutableStateOf(outputDir.listSafely()) }

    LaunchedEffect(outputDir.absolutePath, status) {
        while (true) {
            files = outputDir.listSafely()
            if (status != TabStatus.Running) break
            delay(1500)
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                outputDir.absolutePath,
                style = JewelTheme.defaultTextStyle.copy(color = Color(0xFF666666)),
                modifier = Modifier.weight(1f),
            )
            Text(
                "Open folder",
                style = JewelTheme.defaultTextStyle.copy(color = Color(0xFF2196F3)),
                modifier = Modifier.clickable { openSafely(outputDir) },
            )
        }
        if (files.isEmpty()) {
            EmptyState("Folder is empty.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it.absolutePath }) { ResultsTile(it) }
            }
        }
    }
}

@Composable
private fun ResultsTile(file: File) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(Color(0xFFF2F2F2))
            .clickable { openSafely(file) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (file.isDirectory) "📁" else "📄", style = JewelTheme.defaultTextStyle)
        Spacer(Modifier.width(8.dp))
        Text(
            file.name,
            style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = JewelTheme.defaultTextStyle.copy(color = Color(0xFF666666)))
    }
}

private fun File.listSafely(): List<File> =
    listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()

private fun openSafely(file: File) {
    runCatching { Desktop.getDesktop().open(file) }
}

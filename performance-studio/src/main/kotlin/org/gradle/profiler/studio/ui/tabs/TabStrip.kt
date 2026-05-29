package org.gradle.profiler.studio.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gradle.profiler.studio.domain.TabState
import org.gradle.profiler.studio.domain.TabStatus
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text

@Composable
fun TabStrip(
    tabs: List<TabState>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xFFEDEDED))
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            TabChip(
                tab = tab,
                selected = tab.id == selectedId,
                onSelect = { onSelect(tab.id) },
                onClose = { onClose(tab.id) },
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onNew, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text("+", style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun TabChip(
    tab: TabState,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val bg = if (selected) Color.White else Color.Transparent
    Row(
        Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(bg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusGlyph(tab.status)
        Text(tab.name, style = JewelTheme.defaultTextStyle)
        Text(
            "×",
            style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Bold, color = Color(0xFF666666)),
            modifier = Modifier.clickable { onClose() },
        )
    }
}

@Composable
private fun StatusGlyph(status: TabStatus) {
    val (text, color) = when (status) {
        TabStatus.Editing -> "●" to Color(0xFF9E9E9E)
        TabStatus.Running -> "●" to Color(0xFF2196F3)
        TabStatus.Success -> "✓" to Color(0xFF4CAF50)
        TabStatus.Failure -> "✗" to Color(0xFFF44336)
        TabStatus.Cancelled -> "◐" to Color(0xFF9E9E9E)
    }
    Text(text, style = JewelTheme.defaultTextStyle.copy(color = color))
}

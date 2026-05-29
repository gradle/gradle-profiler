package org.gradle.profiler.studio.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gradle.profiler.studio.domain.TabSection
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun SubTabBar(
    sections: List<TabSection>,
    selected: TabSection,
    onSelect: (TabSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sections.forEach { section ->
            SubTab(
                section = section,
                selected = selected == section,
                onClick = { onSelect(section) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SubTab(
    section: TabSection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            section.label,
            style = JewelTheme.defaultTextStyle.copy(
                color = if (selected) Color(0xFF000000) else Color(0xFF666666),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            modifier = Modifier.padding(vertical = 10.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) Color(0xFF2196F3) else Color.Transparent),
        )
    }
}

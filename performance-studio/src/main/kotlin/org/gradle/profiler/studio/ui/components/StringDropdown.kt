package org.gradle.profiler.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun StringDropdown(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    placeholder: String = "Select…",
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFFCCCCCC), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .background(if (enabled) Color.White else Color(0xFFF5F5F5))
                .clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                selected.ifBlank { placeholder },
                style = JewelTheme.defaultTextStyle.copy(
                    color = if (selected.isBlank()) Color.Gray else Color.Black,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text("▾", style = JewelTheme.defaultTextStyle.copy(color = Color.Gray))
        }
        if (expanded) {
            Popup(
                onDismissRequest = { expanded = false },
                offset = IntOffset(0, 40),
            ) {
                Column(
                    Modifier
                        .widthIn(min = 200.dp)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFCCCCCC), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(vertical = 4.dp),
                ) {
                    options.forEach { opt ->
                        Text(
                            opt,
                            style = JewelTheme.defaultTextStyle.copy(
                                color = if (opt == selected) Color(0xFF2196F3) else Color.Black,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(opt)
                                    expanded = false
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

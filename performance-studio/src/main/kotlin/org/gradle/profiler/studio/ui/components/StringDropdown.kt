package org.gradle.profiler.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import org.gradle.profiler.studio.ui.theme.StudioColors
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
    var triggerWidthPx by remember { mutableStateOf(0) }
    var triggerHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    val palette = StudioColors
    Box(modifier) {
        Row(
            Modifier
                .onSizeChanged {
                    triggerWidthPx = it.width
                    triggerHeightPx = it.height
                }
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .border(1.dp, palette.divider, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .background(if (enabled) palette.windowBg else palette.resultsTile)
                .clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                selected.ifBlank { placeholder },
                style = JewelTheme.defaultTextStyle.copy(
                    color = if (selected.isBlank()) palette.mutedText else palette.textPrimary,
                ),
            )
            Text("▾", style = JewelTheme.defaultTextStyle.copy(color = palette.mutedText))
        }
        if (expanded) {
            val provider = remember(triggerHeightPx) {
                AnchorBelowProvider(triggerHeightPx)
            }
            Popup(
                popupPositionProvider = provider,
                onDismissRequest = { expanded = false },
                focusable = true,
            ) {
                val widthDp = with(density) { triggerWidthPx.toDp() }
                Column(
                    Modifier
                        .width(widthDp)
                        .background(palette.windowBg)
                        .border(
                            1.dp,
                            palette.divider,
                            androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        )
                        .padding(vertical = 4.dp),
                ) {
                    options.forEach { opt ->
                        Text(
                            opt,
                            style = JewelTheme.defaultTextStyle.copy(
                                color = if (opt == selected) palette.accent else palette.textPrimary,
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

private class AnchorBelowProvider(private val anchorHeightPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width)
        val belowY = anchorBounds.top + anchorHeightPx
        val y = if (belowY + popupContentSize.height <= windowSize.height) {
            belowY
        } else {
            (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x.coerceAtLeast(0), y)
    }
}

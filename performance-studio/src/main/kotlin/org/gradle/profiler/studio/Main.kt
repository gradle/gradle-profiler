package org.gradle.profiler.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Gradle Performance Studio",
    ) {
        IntUiTheme(isDark = false) {
            StudioRoot()
        }
    }
}

@Composable
private fun StudioRoot() {
    Row(Modifier.fillMaxSize()) {
        Sidebar()
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column {
                Text("No project selected", style = JewelTheme.defaultTextStyle)
                val profilerHome = ProfilerHome.locate()
                Text(
                    "Profiler home: ${profilerHome?.absolutePath ?: "<not found>"}",
                    style = JewelTheme.defaultTextStyle,
                )
            }
        }
    }
}

@Composable
private fun Sidebar() {
    Column(
        Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(Color(0xFFF2F2F2))
            .padding(12.dp),
    ) {
        Text("PROJECTS", style = JewelTheme.defaultTextStyle)
    }
}

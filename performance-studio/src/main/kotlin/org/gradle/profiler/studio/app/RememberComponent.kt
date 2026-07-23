package org.gradle.profiler.studio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import org.gradle.profiler.studio.mvu.Component

@Composable
fun <C : Component<*, *, *>> rememberComponent(
    vararg keys: Any?,
    factory: () -> C,
): C {
    val component = remember(*keys) { factory() }
    DisposableEffect(component) {
        onDispose { component.close() }
    }
    return component
}

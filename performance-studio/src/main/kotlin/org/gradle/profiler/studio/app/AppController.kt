package org.gradle.profiler.studio.app

import org.gradle.profiler.studio.mvu.Component
import org.gradle.profiler.studio.runner.ConsoleBuffer

class AppController(private val deps: AppDeps) : Component<AppState, AppMessage, AppDeps>(
    initial = initial,
    update = ::update,
    dependencies = deps,
    onError = { it.printStackTrace() },
) {
    fun consoleFor(tabId: Long): ConsoleBuffer = deps.consoles.get(tabId)
}

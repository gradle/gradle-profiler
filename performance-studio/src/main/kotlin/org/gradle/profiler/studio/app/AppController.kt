package org.gradle.profiler.studio.app

import kotlinx.coroutines.flow.StateFlow
import org.gradle.profiler.studio.app.components.sidebar.SidebarComponent
import org.gradle.profiler.studio.app.components.tabs.TabHostComponent

class AppController(deps: AppDeps) {
    val sidebar = SidebarComponent(deps)
    val tabs = TabHostComponent(deps)
    val selectedProject: StateFlow<Int?> = deps.events.projectSelected
    val runningProjects: StateFlow<Set<Int>> = deps.events.runningProjects

    fun close() {
        sidebar.close()
        tabs.close()
    }
}

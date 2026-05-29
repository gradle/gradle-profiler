package org.gradle.profiler.studio.app

import org.gradle.profiler.studio.data.ProjectRepository
import org.gradle.profiler.studio.data.RunRepository
import org.gradle.profiler.studio.runner.ConsoleBuffer
import org.gradle.profiler.studio.runner.ProfilerProcess
import java.util.concurrent.ConcurrentHashMap

class ConsoleRegistry {
    private val map = ConcurrentHashMap<Long, ConsoleBuffer>()
    fun get(tabId: Long): ConsoleBuffer = map.getOrPut(tabId) { ConsoleBuffer() }
    fun remove(tabId: Long) { map.remove(tabId) }
}

class ProcessRegistry {
    private class Entry(val process: ProfilerProcess, @Volatile var cancelled: Boolean = false)
    private val map = ConcurrentHashMap<Long, Entry>()

    fun put(tabId: Long, process: ProfilerProcess) {
        map[tabId] = Entry(process)
    }

    fun cancel(tabId: Long) {
        map[tabId]?.let {
            it.cancelled = true
            it.process.cancel()
        }
    }

    fun wasCancelled(tabId: Long): Boolean = map[tabId]?.cancelled == true

    fun remove(tabId: Long) {
        map.remove(tabId)
    }
}

class AppDeps(
    val projects: ProjectRepository,
    val runs: RunRepository,
    val consoles: ConsoleRegistry,
    val processes: ProcessRegistry,
)

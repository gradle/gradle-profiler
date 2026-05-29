package org.gradle.profiler.studio.runner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ConsoleBuffer(private val maxLines: Int = 50_000) {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun append(line: String) {
        _lines.update { current ->
            if (current.size >= maxLines) (current.drop(current.size - maxLines + 1) + line)
            else (current + line)
        }
    }

    fun clear() {
        _lines.value = emptyList()
    }
}

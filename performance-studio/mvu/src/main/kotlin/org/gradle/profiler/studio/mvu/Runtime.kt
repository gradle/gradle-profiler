package org.gradle.profiler.studio.mvu

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal class Runtime<State : Any, Msg : Any, Deps : Any>(
    initial: Upd<State, Msg, Deps>,
    private val update: (Msg, State) -> Upd<State, Msg, Deps>,
    private val dependencies: Deps,
    private val onError: (Throwable) -> Unit = {},
    runtimeContext: CoroutineContext = Dispatchers.Default,
    private val effectContext: CoroutineContext = Dispatchers.IO,
) {
    private val scope = CoroutineScope(runtimeContext + SupervisorJob())
    private val _state = MutableStateFlow(initial.state)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scheduleEffects(initial.effects)
    }

    fun dispatch(msg: Msg) {
        if (!scope.isActive) return
        scope.launch {
            val current = _state.value
            val next = update(msg, current)
            if (next.state !== current) _state.value = next.state
            scheduleEffects(next.effects)
        }
    }

    fun cancel() {
        scope.cancel()
    }

    private fun scheduleEffects(effects: Set<Effekt<Deps, Msg>>) {
        effects.forEach { effekt ->
            scope.launch(effectContext) {
                try {
                    effekt.run(this, dependencies)?.collect { dispatch(it) }
                } catch (t: Throwable) {
                    onError(t)
                }
            }
        }
    }
}

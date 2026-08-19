package org.gradle.profiler.studio.mvu

import kotlinx.coroutines.flow.StateFlow

abstract class Component<State : Any, Msg : Any, Deps : Any>(
    initial: Upd<State, Msg, Deps>,
    update: (Msg, State) -> Upd<State, Msg, Deps>,
    dependencies: Deps,
    onError: (Throwable) -> Unit = {},
) {
    private val runtime = Runtime(
        initial = initial,
        update = update,
        dependencies = dependencies,
        onError = onError,
    )

    val state: StateFlow<State> = runtime.state

    fun dispatch(msg: Msg) = runtime.dispatch(msg)

    fun close() = runtime.cancel()
}

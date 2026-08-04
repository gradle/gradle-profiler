package org.gradle.profiler.studio.mvu

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

typealias Action<Deps, Msg> = suspend CoroutineScope.(Deps) -> Flow<Msg>?

interface Effekt<Deps, out Msg> {
    val run: Action<Deps, Msg>

    companion object {
        operator fun <Deps, Msg> invoke(action: Action<Deps, Msg>): Effekt<Deps, Msg> =
            object : Effekt<Deps, Msg> {
                override val run = action
            }

        fun <Deps, Msg> single(f: suspend CoroutineScope.(Deps) -> Msg): Effekt<Deps, Msg> =
            Effekt { flowOf(f(it)) }

        fun <Deps, Msg> idle(f: suspend CoroutineScope.(Deps) -> Unit): Effekt<Deps, Msg> =
            Effekt { f(it); null }

        fun <Deps, Msg> flow(f: suspend CoroutineScope.(Deps) -> Flow<Msg>): Effekt<Deps, Msg> =
            Effekt { f(it) }
    }
}

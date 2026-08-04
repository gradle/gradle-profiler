package org.gradle.profiler.studio.mvu

data class Upd<State, Msg, Deps>(
    val state: State,
    val effects: Set<Effekt<Deps, Msg>> = emptySet(),
)

infix fun <State, Msg, Deps> State.with(effect: Effekt<Deps, Msg>): Upd<State, Msg, Deps> =
    Upd(this, setOf(effect))

infix fun <State, Msg, Deps> State.with(effects: Set<Effekt<Deps, Msg>>): Upd<State, Msg, Deps> =
    Upd(this, effects)

fun <Msg, Deps> noEffects(): Set<Effekt<Deps, Msg>> = emptySet()

infix fun <State, Msg, Deps> Upd<State, Msg, Deps>.plusEffects(
    newEffects: Set<Effekt<Deps, Msg>>,
): Upd<State, Msg, Deps> =
    if (newEffects.isEmpty()) this else state with (effects + newEffects)

package org.gradle.profiler.studio.domain

data class ConfigDraft(
    val tasks: String = "",
    val warmups: String = "3",
    val iterations: String = "10",
    val mode: Mode = Mode.Benchmark,
    val profiler: String = "jfr",
    val gradleArgs: String = "",
    val jvmArgs: String = "",
    val daemon: Daemon = Daemon.Warm,
    val runUsing: RunUsing = RunUsing.ToolingApi,
    val gradleUserHome: String = "",
    val mutators: List<MutatorEntry> = emptyList(),
)

enum class Mode(val label: String) { Benchmark("Benchmark"), Profile("Profile") }
enum class Daemon(val label: String) { Warm("Warm"), Cold("Cold"), None("None") }
enum class RunUsing(val label: String) { ToolingApi("Tooling API"), Cli("CLI"), NoDaemon("No daemon") }

data class MutatorEntry(val type: String, val relativePath: String)

package org.gradle.profiler.studio.runner

import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import org.gradle.profiler.studio.domain.ConfigDraft
import org.gradle.profiler.studio.domain.RunUsing
import java.io.File

object HoconWriter {
    const val SCENARIO_NAME = "default"

    fun write(draft: ConfigDraft, target: File) {
        val scenario = buildMap<String, Any> {
            put("tasks", draft.tasks.splitTokens())
            put("warm-ups", draft.warmups.toIntOrNull() ?: 0)
            put("iterations", draft.iterations.toIntOrNull() ?: 1)
            if (draft.gradleArgs.isNotBlank()) put("gradle-args", draft.gradleArgs.splitTokens())
            if (draft.jvmArgs.isNotBlank()) put("jvm-args", draft.jvmArgs.splitTokens())
            put("daemon", draft.daemon.name.lowercase())
            put("run-using", draft.runUsing.toScenarioValue())
            draft.mutators.forEach { put(it.type, it.relativePath) }
        }
        val root = ConfigValueFactory.fromMap(mapOf(SCENARIO_NAME to scenario))
        val rendered = root.render(
            ConfigRenderOptions.defaults()
                .setOriginComments(false)
                .setComments(false)
                .setJson(false),
        )
        target.parentFile?.mkdirs()
        target.writeText(rendered)
    }

    private fun String.splitTokens(): List<String> =
        trim().split("\\s+".toRegex()).filter(String::isNotBlank)

    private fun RunUsing.toScenarioValue(): String = when (this) {
        RunUsing.ToolingApi -> "tooling-api"
        RunUsing.Cli -> "cli"
        RunUsing.NoDaemon -> "no-daemon"
    }
}

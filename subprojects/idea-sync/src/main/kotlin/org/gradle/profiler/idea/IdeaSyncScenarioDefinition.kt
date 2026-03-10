package org.gradle.profiler.idea

import com.typesafe.config.Config
import org.gradle.profiler.ConfigUtil

private const val IDEA_JVM_ARGS_KEY = "idea-jvm-args"

class IdeaSyncScenarioDefinition(
    val ideaJvmArgs: List<String>
) {
    companion object {
        const val IDEA_SYNC_KEY = "idea-sync"

        @JvmStatic
        val allKeys: List<String> = listOf(
            IDEA_SYNC_KEY,
            IDEA_JVM_ARGS_KEY
        )

        @JvmStatic
        fun ofConfig(config: Config): IdeaSyncScenarioDefinition =
            IdeaSyncScenarioDefinition(
                ideaJvmArgs = ConfigUtil.strings(config, IDEA_JVM_ARGS_KEY)
            )
    }
}

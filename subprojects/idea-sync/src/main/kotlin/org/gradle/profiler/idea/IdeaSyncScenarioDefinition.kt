package org.gradle.profiler.idea

import com.typesafe.config.Config

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
        fun ofConfig(config: Config): IdeaSyncScenarioDefinition = with(config) {
            IdeaSyncScenarioDefinition(
                ideaJvmArgs = emptyList() //TODO
            )
        }
    }
}

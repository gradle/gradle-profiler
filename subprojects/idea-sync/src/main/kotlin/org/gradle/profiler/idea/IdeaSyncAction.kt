package org.gradle.profiler.idea

import org.gradle.profiler.BuildAction
import org.gradle.profiler.GradleClient
import org.gradle.profiler.result.BuildActionResult

class IdeaSyncAction : BuildAction {

    override fun isDoesSomething(): Boolean = true

    override fun getDisplayName(): String = "IDEA Sync"

    override fun getShortDisplayName(): String = displayName

    override fun run(
        gradleClient: GradleClient,
        gradleArgs: List<String>,
        jvmArgs: List<String>
    ): BuildActionResult = (gradleClient as IdeaGradleClient).sync(gradleArgs, jvmArgs)
}

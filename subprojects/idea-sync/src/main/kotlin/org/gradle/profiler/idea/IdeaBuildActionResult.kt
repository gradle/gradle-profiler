package org.gradle.profiler.idea

import org.gradle.profiler.result.BuildActionResult
import java.time.Duration

class IdeaBuildActionResult(
    val gradleExecutionTime: Duration,
    executionTime: Duration
) : BuildActionResult(executionTime) {

    val ideExecutionTime: Duration = executionTime - gradleExecutionTime
}

package org.gradle.profiler.idea

import java.time.Duration
import org.gradle.profiler.result.BuildActionResult

class IdeaBuildActionResult(
    val gradleExecutionTime: Duration,
    executionTime: Duration
) : BuildActionResult(executionTime) {

    val ideExecutionTime: Duration = executionTime - gradleExecutionTime
}

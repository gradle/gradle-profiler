package org.gradle.profiler.idea

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.areIndicatorsVisible
import com.intellij.driver.sdk.isProjectOpened
import com.intellij.driver.sdk.waitFor
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration

internal fun Driver.waitForImportFinished(
    project: Project,
    timeout: Duration,
    noIndicatorsThreshold: Duration,
): Instant {
    var noIndicatorsSince: Instant? = null

    waitFor(
        message = "Import finished",
        timeout = timeout.toKotlinDuration()
    ) {
        if (!isProjectOpened(project) || areIndicatorsVisible(project)) {
            noIndicatorsSince = null
            return@waitFor false
        }

        // No activity.
        val quietPeriodStart = noIndicatorsSince
        if (quietPeriodStart == null) {
            noIndicatorsSince = Instant.now()
            // Just started a quiet period, continue waiting.
            return@waitFor false
        }

        // We are in a quiet period, check if it's long enough.
        val quietPeriodDuration = Duration.between(quietPeriodStart, Instant.now())
        return@waitFor quietPeriodDuration >= noIndicatorsThreshold // Quiet for long enough.
    }

    return noIndicatorsSince ?: error("Failed to wait project import finish")
}

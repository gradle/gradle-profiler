package org.gradle.profiler.intellij.plugin.system

import org.gradle.profiler.client.protocol.messages.IdeSyncRequestCompleted.IdeSyncRequestResult

data class GradleSyncResult(
    val result: IdeSyncRequestResult,
    val errorMessage: String
)

package org.gradle.profiler.intellij.plugin

import org.gradle.profiler.client.protocol.ServerConnection
import org.gradle.profiler.client.protocol.messages.IdeRequest
import org.gradle.profiler.client.protocol.messages.IdeRequest.IdeRequestType.CLEANUP_CACHE
import org.gradle.profiler.client.protocol.messages.IdeRequest.IdeRequestType.STOP_RECEIVING_EVENTS
import org.gradle.profiler.client.protocol.messages.IdeRequest.IdeRequestType.SYNC
import org.gradle.profiler.client.protocol.messages.IdeSyncRequestCompleted.IdeSyncRequestResult.FAILED
import org.gradle.profiler.client.protocol.messages.IdeSyncRequestCompleted.IdeSyncRequestResult.SUCCEEDED
import java.time.Duration

/**
 * Integration tests for the plugin's in-IDE request handling.
 *
 * Methods are named test* (JUnit 3 convention) because IntelliJ's
 * HeavyPlatformTestCase hierarchy is discovered via JUnit38ClassRunner,
 * which requires the test* naming convention rather than @Test annotations.
 *
 * These tests trigger real Gradle syncs (daemon startup, build evaluation, etc.)
 * and therefore use generous timeouts.
 */
class IdePluginIntegrationTest : IdePluginSpecification() {

    private companion object {
        // Real Gradle syncs can take several minutes on a cold start (Gradle daemon
        // startup, first-run script compilation, etc.).
        val SYNC_TIMEOUT: Duration = Duration.ofMinutes(10)
    }

    fun testShouldSuccessfullySyncGradleProject() {
        val connection: ServerConnection = server.waitForIncoming(Duration.ofSeconds(30))
        connection.send(IdeRequest(SYNC))

        val result = connection.receiveSyncRequestCompleted(SYNC_TIMEOUT)
        assertEquals(SUCCEEDED, result.result)

        connection.send(IdeRequest(STOP_RECEIVING_EVENTS))
    }

    fun testShouldReportErrorIfGradleSyncIsNotSuccessful() {
        val connection: ServerConnection = server.waitForIncoming(Duration.ofSeconds(30))

        // Complete the startup sync first with a valid build.
        connection.send(IdeRequest(SYNC))
        val startupResult = connection.receiveSyncRequestCompleted(SYNC_TIMEOUT)
        assertEquals(SUCCEEDED, startupResult.result)

        // Now break the settings file and trigger a fresh manual sync.
        settingsFile.writeText("garbage")
        connection.send(IdeRequest(SYNC))

        val failedResult = connection.receiveSyncRequestCompleted(SYNC_TIMEOUT)
        assertEquals(FAILED, failedResult.result)

        connection.send(IdeRequest(STOP_RECEIVING_EVENTS))
    }

    fun testShouldCleanIdeCache() {
        val connection: ServerConnection = server.waitForIncoming(Duration.ofSeconds(30))

        // Verify two consecutive CLEANUP_CACHE requests both complete successfully.
        // Actual cache invalidation is delegated to CachesInvalidator extensions
        // registered by IntelliJ Platform and its plugins.
        connection.send(IdeRequest(CLEANUP_CACHE))
        connection.receiveCacheCleanupCompleted(Duration.ofSeconds(30))

        connection.send(IdeRequest(CLEANUP_CACHE))
        connection.receiveCacheCleanupCompleted(Duration.ofSeconds(30))

        connection.send(IdeRequest(STOP_RECEIVING_EVENTS))
    }
}

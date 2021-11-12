package org.gradle.profiler.studio.plugin


import org.gradle.profiler.client.protocol.ServerConnection
import org.gradle.profiler.client.protocol.messages.StudioRequest
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted
import org.junit.Test

import java.time.Duration

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.STOP_RECEIVING_EVENTS
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.SYNC
import static org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult.FAILED
import static org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult.SUCCEEDED

class StudioPluginIntegrationTest extends StudioPluginTestCase {

    @Test
    void "should successfully sync Gradle project"() {
        given:
        ServerConnection connection = server.waitForIncoming(Duration.ofSeconds(10))
        connection.receiveProjectOpened(Duration.ofSeconds(10));

        when:
        connection.send(new StudioRequest(SYNC))

        then:
        StudioSyncRequestCompleted requestCompleted = connection.receiveSyncRequestCompleted(Duration.ofSeconds(60))
        requestCompleted.result == SUCCEEDED

        and:
        connection.send(new StudioRequest(STOP_RECEIVING_EVENTS))
    }

    @Test
    void "should report error if Gradle sync is not successful"() {
        given:
        ServerConnection connection = server.waitForIncoming(Duration.ofSeconds(10))
        connection.receiveProjectOpened(Duration.ofSeconds(10))
        // Fail Gradle build by removing settings file
        settingsFile.delete()

        when:
        connection.send(new StudioRequest(SYNC))

        then:
        StudioSyncRequestCompleted requestCompleted = connection.receiveSyncRequestCompleted(Duration.ofSeconds(60))
        requestCompleted.result == FAILED

        and:
        connection.send(new StudioRequest(STOP_RECEIVING_EVENTS))
    }

}

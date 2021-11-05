package org.gradle.profiler.studio.plugin

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.gradle.profiler.client.protocol.Server
import org.gradle.profiler.client.protocol.ServerConnection
import org.gradle.profiler.client.protocol.messages.StudioRequest
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted

import java.time.Duration

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.SYNC
import static org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult.SUCCEEDED
import static org.gradle.profiler.studio.plugin.client.GradleProfilerClient.PROFILER_PORT_PROPERTY

/**
 * Note: This class uses Junit3 so every test should start with "test"
 */
class StudioPluginIntegrationTest extends LightJavaCodeInsightFixtureTestCase {

    private Server server

    @Override
    void setUp() throws Exception {
        server = new Server("plugin")
        System.setProperty(PROFILER_PORT_PROPERTY, Integer.toString(server.getPort()))
        System.setProperty(INTEGRATION_TEST_PROPERTY, "true");
        super.setUp()
    }

    void "test project sync succeeds"() {
        given:
        ServerConnection connection = server.waitForIncoming(Duration.ofSeconds(10))

        when:
        connection.send(new StudioRequest(SYNC))

        then:
        StudioSyncRequestCompleted requestCompleted = connection.receiveSyncRequestCompleted(Duration.ofSeconds(10))
        requestCompleted.result == SUCCEEDED

        and:
        connection.send(new StudioRequest(EXIT))
    }

}

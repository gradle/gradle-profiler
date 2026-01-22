package org.gradle.profiler.studio.plugin

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.ProjectUtil
import com.intellij.openapi.util.registry.Registry
import org.gradle.profiler.client.protocol.ServerConnection
import org.gradle.profiler.client.protocol.messages.StudioRequest
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted

import java.nio.file.Paths
import java.time.Duration

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.SYNC
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.CLEANUP_CACHE
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.STOP_RECEIVING_EVENTS
import static org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult.FAILED
import static org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult.SUCCEEDED

class StudioPluginIntegrationTest extends StudioPluginSpecification {

    def setup() {
        // Our plugin expects auto-import is disabled
        Registry.get("external.system.auto.import.disabled").setValue(true)
    }

    def "should successfully sync Gradle project"() {
        given:
        ServerConnection connection = server.waitForIncoming(Duration.ofSeconds(10))

        when:
        connection.send(new StudioRequest(SYNC))

        then:
        StudioSyncRequestCompleted requestCompleted = connection.receiveSyncRequestCompleted(Duration.ofSeconds(150))
        requestCompleted.result == SUCCEEDED

        and:
        connection.send(new StudioRequest(STOP_RECEIVING_EVENTS))
    }

    def "should report error if Gradle sync is not successful"() {
        given:
        ServerConnection connection = server.waitForIncoming(Duration.ofSeconds(10))
        // Fail Gradle build by failing the build script compilation
        settingsFile.text = "garbage"

        when:
        connection.send(new StudioRequest(SYNC))

        then:
        StudioSyncRequestCompleted requestCompleted = connection.receiveSyncRequestCompleted(Duration.ofSeconds(150))
        requestCompleted.result == FAILED

        and:
        connection.send(new StudioRequest(STOP_RECEIVING_EVENTS))
    }

    def "should clean IDE cache"() {
        given:
        ServerConnection connection = server.waitForIncoming(Duration.ofSeconds(10))

        when:
        def invalidateFile = ApplicationInfo.instance.majorVersion == "2021" && ApplicationInfo.instance.minorVersionMainPart in ["0", "1"]
            ? Paths.get(PathManager.getSystemPath(), "projectModelCache", ".invalidate").toFile()
            : ProjectUtil.projectsDataDir.resolve(".invalidate").toFile()
        long beforeTimestamp = invalidateFile.exists() ? invalidateFile.text as Long : 0
        connection.send(new StudioRequest(CLEANUP_CACHE))
        connection.receiveCacheCleanupCompleted(Duration.ofSeconds(10))
        long afterFirstTimestamp = invalidateFile.text as Long
        connection.send(new StudioRequest(CLEANUP_CACHE))
        connection.receiveCacheCleanupCompleted(Duration.ofSeconds(10))
        long afterSecondTimestamp = invalidateFile.text as Long

        then:
        beforeTimestamp < afterFirstTimestamp
        afterFirstTimestamp < afterSecondTimestamp

        and:
        connection.send(new StudioRequest(STOP_RECEIVING_EVENTS))
    }
}

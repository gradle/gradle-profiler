package org.gradle.profiler.studio.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.OpenProjectTaskBuilder
import org.apache.commons.io.FileUtils
import org.gradle.profiler.client.protocol.Server
import org.gradle.profiler.client.protocol.ServerConnection
import org.gradle.profiler.client.protocol.messages.StudioRequest
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted
import org.jetbrains.annotations.NotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Path
import java.time.Duration

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.STOP_RECEIVING_EVENTS
import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.SYNC
import static org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult.SUCCEEDED

@RunWith(JUnit4.class)
class StudioPluginIntegrationTest extends HeavyPlatformTestCase {

    private Server server

    @Override
    protected void setUp() throws Exception {
        server = new Server("plugin")
        System.setProperty("gradle.profiler.port", server.getPort() as String)
        // We must run this on Edt otherwise the exception is thrown since runInDispatchThread is set to false
        EdtTestUtil.runInEdtAndWait { super.setUp() }
    }

    @Override
    protected void tearDown() throws Exception {
        // We must run this on Edt otherwise the exception is thrown since runInDispatchThread is set to false
        EdtTestUtil.runInEdtAndWait { super.tearDown() }
    }

    @Test
    void "should successfully sync Gradle project"() {
        given:
        ServerConnection connection = server.waitForIncoming(Duration.ofSeconds(10))

        when:
        connection.send(new StudioRequest(SYNC))

        then:
        StudioSyncRequestCompleted requestCompleted = connection.receiveSyncRequestCompleted(Duration.ofSeconds(60))
        requestCompleted.result == SUCCEEDED

        and:
        connection.send(new StudioRequest(STOP_RECEIVING_EVENTS))
    }

    @Override
    protected @NotNull Project doCreateAndOpenProject() {
        OpenProjectTaskBuilder optionBuilder = getOpenProjectOptions()
        Path projectFile = getProjectDirOrFile(isCreateDirectoryBasedProject())
        FileUtils.copyDirectory(Path.of(".", "src", "test", "resources", "test-project").toFile(), getProjectDirOrFile().parent.toFile())
        return Objects.requireNonNull(ProjectManagerEx.getInstanceEx().openProject(projectFile, optionBuilder.build()));
    }

    /**
     * This is needed to be false since we have to wait on plugin response from different thread than IDE is running.
     */
    @Override
    protected boolean runInDispatchThread() {
        return false
    }

}

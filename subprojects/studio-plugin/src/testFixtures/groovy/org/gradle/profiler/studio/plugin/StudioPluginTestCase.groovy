package org.gradle.profiler.studio.plugin

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.OpenProjectTaskBuilder
import org.gradle.profiler.client.protocol.Server
import org.gradle.profiler.client.protocol.ServerConnection
import org.jetbrains.annotations.NotNull
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Path
import java.time.Duration

@RunWith(JUnit4.class)
class StudioPluginTestCase extends HeavyPlatformTestCase {

    Server server
    ServerConnection connection
    File buildFile
    File settingsFile
    File projectDir

    @Override
    protected void setUp() throws Exception {
        server = new Server("plugin")
        connection = server.waitForIncoming(Duration.ofSeconds(10))
        System.setProperty("gradle.profiler.port", server.getPort() as String)
        // We must run this on Edt otherwise the exception is thrown since runInDispatchThread is set to false
        EdtTestUtil.runInEdtAndWait { super.setUp() }
    }

    @Override
    protected void tearDown() throws Exception {
        // We must run this on Edt otherwise the exception is thrown since runInDispatchThread is set to false
        EdtTestUtil.runInEdtAndWait {
            ProjectJdkTable jdkTable = ProjectJdkTable.getInstance()
            jdkTable.getAllJdks().each { Sdk sdk ->
                WriteAction.run { jdkTable.removeJdk(sdk) }
            }
            super.tearDown()
        }
    }

    /**
     * This is called from setUp() method.
     */
    @Override
    protected @NotNull Project doCreateAndOpenProject() {
        OpenProjectTaskBuilder optionBuilder = getOpenProjectOptions()
        Path projectFile = getProjectDirOrFile(isCreateDirectoryBasedProject())
        // We have to create a project with build.gradle file
        // before it is open by IDE, so IDE detects it as a Gradle project
        createGradleProject()
        return Objects.requireNonNull(ProjectManagerEx.getInstanceEx().openProject(projectFile, optionBuilder.build()));
    }

    protected void createGradleProject() {
        projectDir = getProjectDirOrFile().parent.toFile()
        projectDir.mkdirs()
        println(projectDir.absolutePath)
        buildFile = new File(projectDir, "build.gradle")
        settingsFile = new File(projectDir, "settings.gradle")
        buildFile.createNewFile()
        settingsFile.createNewFile()
    }

    /**
     * This is needed to be false since we have to wait on plugin response from different thread than IDE is running.
     */
    @Override
    protected boolean runInDispatchThread() {
        return false
    }

}

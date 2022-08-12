package org.gradle.profiler.studio.plugin

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.OpenProjectTaskBuilder
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.NotNull
import org.junit.runner.Description

import java.nio.file.Path
import java.util.function.Consumer

/**
 * HeavyPlatformTestCase extends Junit TestCase, but we use it through composition so we can use Spock framework
 */
@SuppressWarnings('UnconstructableJUnitTestCase')
class IdeSetupHelper extends HeavyPlatformTestCase {

    Consumer<File> projectCreator

    IdeSetupHelper(Description description, Consumer<File> projectCreator) {
        super.setName(description.methodName)
        this.projectCreator = projectCreator
    }

    @Override
    void runBare(@NotNull ThrowableRunnable<Throwable> testRunnable) {
        super.runBare(testRunnable)
    }

    @Override
    void setUp() {
        // We must run this on Edt otherwise the exception is thrown since runInDispatchThread is set to false
        EdtTestUtil.runInEdtAndWait {
            super.setUp()
        }
    }

    @Override
    void tearDown() {
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
        // We have to create a project with settings.gradle file
        // before it is open by IDE, so IDE detects it as a Gradle project
        projectCreator.accept(projectFile.parent.toFile())
        Project project = ProjectManagerEx.getInstanceEx().openProject(projectFile, optionBuilder.build())
        return project
    }

    /**
     * This is needed to be false since we have to wait on plugin response from different thread than IDE is running.
     */
    @Override
    protected boolean runInDispatchThread() {
        return false
    }
}

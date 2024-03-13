package org.gradle.profiler.studio.plugin

import com.android.tools.idea.sdk.Jdks
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.OpenProjectTaskBuilder
import com.intellij.util.ThrowableRunnable
import org.gradle.internal.jvm.Jvm
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.rules.TemporaryFolder
import org.junit.runner.Description

import java.util.function.Consumer
/**
 * HeavyPlatformTestCase extends Junit TestCase, but we use it through composition so we can use Spock framework
 */
@SuppressWarnings('UnconstructableJUnitTestCase')
class IdeSetupHelper extends HeavyPlatformTestCase {

    Consumer<File> projectCreator
    TemporaryFolder temporaryFolder
    Sdk jdk

    IdeSetupHelper(Description description, TemporaryFolder tempFolder, Consumer<File> projectCreator) {
        super.setName(description.methodName)
        this.projectCreator = projectCreator
        this.temporaryFolder = tempFolder
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
            // Setup Project JDK to current test JDK, so IntelliJ doesn't try to discover all JDKs on the system
            WriteAction.run {
                jdk = Jdks.instance.createJdk(Jvm.current().javaHome.absolutePath)
                ProjectRootManager.getInstance(project).setProjectSdk(jdk)
            }
            GradleSettings gradleSettings = GradleSettings.getInstance(project);
            gradleSettings.subscribe(new DefaultGradleSettingsListener() {
                @Override
                void onProjectsLinked(@NotNull Collection<GradleProjectSettings> linkedProjectsSettings) {
                    linkedProjectsSettings.each { it.gradleJvm = ExternalSystemJdkUtil.USE_PROJECT_JDK }
                }
            }, gradleSettings);
        }
    }

    @Override
    void tearDown() {
        // We must run this on Edt otherwise the exception is thrown since runInDispatchThread is set to false
        EdtTestUtil.runInEdtAndWait {
            WriteAction.run {
                ProjectJdkTable.instance.removeJdk(jdk)
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
        File projectRoot = temporaryFolder.newFolder()
        // We have to create a project with settings.gradle file
        // before it is open by IDE, so IDE detects it as a Gradle project
        projectCreator.accept(projectRoot)
        Project project = ProjectManagerEx.getInstanceEx().openProject(projectRoot.toPath(), optionBuilder.build())
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

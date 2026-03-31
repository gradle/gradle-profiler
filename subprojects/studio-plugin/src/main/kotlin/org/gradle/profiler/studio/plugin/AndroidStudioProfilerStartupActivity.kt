package org.gradle.profiler.studio.plugin

import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Android Studio-specific setup that prevents the built-in startup activity from
 * triggering an automatic Gradle sync before the profiler can control it.
 *
 * Loaded only in Android Studio via optional dependency on `org.jetbrains.android`
 * (see `android-studio-extensions.xml`).
 */
class AndroidStudioProfilerStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (System.getProperty(GradleProfilerStartupActivity.PROFILER_PORT_PROPERTY) != null) {
            // https://github.com/gradle/gradle-profiler/issues/754
            GradleProjectInfo.getInstance(project).isSkipStartupActivity = true
        }
    }
}

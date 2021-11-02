package org.gradle.profiler.studio.plugin;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.project.ProjectManagerListener;
import org.gradle.profiler.studio.plugin.client.GradleProfilerClient;

public class GradleProfilerProjectManagerListener implements ProjectManagerListener {

    @Override
    public void projectOpened(com.intellij.openapi.project.Project project) {
        System.out.println("Project opened");
        TrustedProjects.setTrusted(project, true);
        new GradleProfilerClient().connectToProfilerAsync(project);
    }

}

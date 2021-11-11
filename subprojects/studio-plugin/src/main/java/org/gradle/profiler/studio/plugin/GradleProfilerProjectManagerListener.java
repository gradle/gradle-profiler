package org.gradle.profiler.studio.plugin;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManagerListener;
import org.gradle.profiler.studio.plugin.client.GradleProfilerClient;

public class GradleProfilerProjectManagerListener implements ProjectManagerListener {

    private static final Logger LOG = Logger.getInstance(GradleProfilerProjectManagerListener.class);

    @Override
    public void projectOpened(com.intellij.openapi.project.Project project) {
        LOG.info("Project opened");
        TrustedProjects.setTrusted(project, true);
        new GradleProfilerClient().connectToProfilerAsync(project);
    }

}

package org.gradle.profiler.studio.plugin;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManagerListener;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.studio.plugin.client.GradleProfilerClient;
import org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT_IDE;

public class GradleProfilerProjectManagerListener implements ProjectManagerListener {

    private static final Logger LOG = Logger.getInstance(GradleProfilerProjectManagerListener.class);

    public static final String PROFILER_PORT_PROPERTY = "gradle.profiler.port";

    @Override
    public void projectOpened(@NotNull com.intellij.openapi.project.Project project) {
        LOG.info("Project opened");
        if (System.getProperty(PROFILER_PORT_PROPERTY) != null) {
            // This solves the issue where Android Studio would run the Gradle sync automatically on the first import.
            // Unfortunately it seems we can't always detect it because it happens very late and due to that there might
            // a case where two simultaneous syncs would be run: one from automatic sync trigger and one from our trigger.
            // With this line we disable that automatic sync, but we still trigger our sync later in the code.
            GradleProjectInfo.getInstance(project).setSkipStartupActivity(true);
            TrustedProjects.setTrusted(project, true);
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                StudioRequest lastRequest = listenForSyncRequests(project);
                if (lastRequest.getType() == EXIT_IDE) {
                    AndroidStudioSystemHelper.exit(project);
                }
            });
        }
    }

    private StudioRequest listenForSyncRequests(@NotNull com.intellij.openapi.project.Project project) {
        int port = Integer.getInteger(PROFILER_PORT_PROPERTY);
        try (Client client = new Client(port)) {
            return new GradleProfilerClient(client).listenForSyncRequests(project);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

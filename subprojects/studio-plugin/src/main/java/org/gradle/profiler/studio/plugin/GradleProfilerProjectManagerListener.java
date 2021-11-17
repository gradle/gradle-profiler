package org.gradle.profiler.studio.plugin;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.ProjectManagerListener;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.studio.plugin.client.GradleProfilerClient;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType.EXIT_IDE;
import static org.gradle.profiler.studio.plugin.system.AndroidStudioSystemHelper.exit;

public class GradleProfilerProjectManagerListener extends PreloadingActivity implements ProjectManagerListener {

    private static final Logger LOG = Logger.getInstance(GradleProfilerProjectManagerListener.class);

    public static final String PROFILER_PORT_PROPERTY = "gradle.profiler.port";
    private static final String STARTUP_PORT_PROPERTY = "gradle.profiler.startup.port";

    /**
     * Preload is started as soon as IDE starts. We use it, so we can detect fast if IDE was started or not.
     */
    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
        if (System.getProperty(STARTUP_PORT_PROPERTY) != null) {
            int port = Integer.getInteger(STARTUP_PORT_PROPERTY);
            try (Client ignored = new Client(port)) {
                LOG.info("Startup check connected to port: " + port);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public void projectOpened(@NotNull com.intellij.openapi.project.Project project) {
        LOG.info("Project opened");
        if (System.getProperty(PROFILER_PORT_PROPERTY) != null) {
            TrustedProjects.setTrusted(project, true);
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                StudioRequest lastRequest = listenForSyncRequests(project);
                if (lastRequest.getType() == EXIT_IDE) {
                    exit();
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

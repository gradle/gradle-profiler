package org.gradle.profiler.studio.plugin;

import com.google.common.base.Suppliers;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.ProjectManagerListener;
import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.studio.plugin.client.GradleProfilerClient;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Supplier;

import static org.gradle.profiler.studio.plugin.client.GradleProfilerClient.PROFILER_PORT_PROPERTY;

public class GradleProfilerProjectManagerListener extends PreloadingActivity implements ProjectManagerListener {

    private static final Logger LOG = Logger.getInstance(GradleProfilerProjectManagerListener.class);
    private static final Supplier<Optional<Client>> CLIENT_PROVIDER = Suppliers.memoize(GradleProfilerProjectManagerListener::getConnectedClient);

    /**
     * Preload is started as soon as possible of IDE start. It's used so we don't have to wait long for client connection.
     */
    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
        CLIENT_PROVIDER.get();
    }

    @Override
    public void projectOpened(com.intellij.openapi.project.Project project) {
        LOG.info("Project opened");
        if (CLIENT_PROVIDER.get().isPresent()) {
            TrustedProjects.setTrusted(project, true);
            new GradleProfilerClient(CLIENT_PROVIDER.get().get()).listenForSyncRequests(project);
        }
    }

    private static Optional<Client> getConnectedClient() {
        if (System.getProperty(PROFILER_PORT_PROPERTY) == null) {
            return Optional.empty();
        }

        int port = Integer.parseInt(System.getProperty(PROFILER_PORT_PROPERTY));
        Client.INSTANCE.connect(port);
        LOG.info("Connected to port: " + System.getProperty(PROFILER_PORT_PROPERTY));
        return Optional.of(Client.INSTANCE);
    }

}

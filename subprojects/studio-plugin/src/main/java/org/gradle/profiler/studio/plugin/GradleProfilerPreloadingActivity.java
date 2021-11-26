package org.gradle.profiler.studio.plugin;

import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.gradle.profiler.client.protocol.Client;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

public class GradleProfilerPreloadingActivity extends PreloadingActivity {

    private static final Logger LOG = Logger.getInstance(GradleProfilerProjectManagerListener.class);

    private static final String STARTUP_PORT_PROPERTY = "gradle.profiler.startup.port";

    /**
     * Preload is started as soon as IDE starts. We use it, so we can detect fast if IDE was started or not.
     */
    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
        LOG.info("Preloading...");
        if (System.getProperty(STARTUP_PORT_PROPERTY) != null) {
            int port = Integer.getInteger(STARTUP_PORT_PROPERTY);
            try (Client ignored = new Client(port)) {
                LOG.info("Startup check connected to port: " + port);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}

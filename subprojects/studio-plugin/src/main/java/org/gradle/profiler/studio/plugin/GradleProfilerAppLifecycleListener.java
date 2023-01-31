package org.gradle.profiler.studio.plugin;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.diagnostic.Logger;
import org.gradle.profiler.client.protocol.Client;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

public class GradleProfilerAppLifecycleListener implements AppLifecycleListener {

    private static final Logger LOG = Logger.getInstance(GradleProfilerStartupActivity.class);

    private static final String STARTUP_PORT_PROPERTY = "gradle.profiler.startup.port";

    /**
     * AppFrameCreated is called as soon as IDE starts. We use it, so we can detect fast if IDE was started or not.
     */
    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
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

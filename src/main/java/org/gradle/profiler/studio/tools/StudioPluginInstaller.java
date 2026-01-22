package org.gradle.profiler.studio.tools;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Installs the Android Studio plugin into the plugins directory.
 */
public class StudioPluginInstaller {

    private static final String PLUGIN_INSTALL_FOLDER_NAME = "gradle-profiler-studio-plugin";

    private final Path pluginInstallDir;

    public StudioPluginInstaller(Path studioPluginsDir) {
        this.pluginInstallDir = new File(studioPluginsDir.toFile(), PLUGIN_INSTALL_FOLDER_NAME).toPath();
    }

    public void installPlugin(List<Path> pluginJars) {
        try {
            // Delete previous directory in case it was not deleted before
            FileUtils.deleteDirectory(pluginInstallDir.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete previous gradle-profiler plugin installation. " +
                "This might indicate that another process with gradle-profiler plugin is running in the same sandbox.", e);
        }
        installPluginToDirectory(pluginJars);
    }

    private void installPluginToDirectory(List<Path> jars) {
        try {
            for (Path jar : jars) {
                String jarName = jar.getFileName().toString();
                FileUtils.copyFile(jar.toFile(), Paths.get(pluginInstallDir.toAbsolutePath().toString(), "lib", jarName).toFile());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * We delete plugins Quietly at uninstall, since we don't want gradle-profiler to fail in that case.
     */
    public void uninstallPlugin() {
        FileUtils.deleteQuietly(pluginInstallDir.toFile());
    }
}

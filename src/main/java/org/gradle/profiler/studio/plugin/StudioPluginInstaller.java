package org.gradle.profiler.studio.plugin;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

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
        // Delete previous directory in case it was not deleted before
        uninstallPlugin();
        installPluginToDirectory(pluginJars);
    }

    private void installPluginToDirectory(List<Path> jars) {
        try {
            for (Path jar : jars) {
                String jarName = jar.getFileName().toString();
                checkArgument(jarName.endsWith(".jar"), "Expected jar file: %s to end with .jar", jar);
                FileUtils.copyFile(jar.toFile(), Paths.get(pluginInstallDir.toAbsolutePath().toString(), "lib", jarName).toFile());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void uninstallPlugin() {
        try {
            FileUtils.deleteDirectory(pluginInstallDir.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}

package org.gradle.profiler.studio.plugin;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.studio.LaunchConfiguration;

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

    /**
     * Note: if you change this constant, and you load plugins always from the same location,
     * then you will have also a plugin installed before on the classpath, and it might override your new classes.
     */
    private static final String PLUGIN_INSTALL_FOLDER_NAME = "gradle-profiler-studio-plugin";

    public void installPlugin(LaunchConfiguration configuration) {
        // Delete previous directory in case the plugin directory is reused
        deletePluginDirectory(configuration.getStudioPluginsDir());
        installPluginToDirectory(
            configuration.getStudioPluginsDir(),
            configuration.getStudioPluginJars()
        );
    }

    private static void installPluginToDirectory(Path directory, List<Path> jars) {
        try {
            for (Path jar : jars) {
                String jarName = jar.getFileName().toString();
                checkArgument(jarName.endsWith(".jar"), "Expected jar file: %s to end with .jar", jar);
                FileUtils.copyFile(jar.toFile(), Paths.get(directory.toAbsolutePath().toString(), PLUGIN_INSTALL_FOLDER_NAME, "lib", jarName).toFile());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deletePluginDirectory(Path pluginsDir) {
        try {
            FileUtils.deleteDirectory(Paths.get(pluginsDir.toString(), PLUGIN_INSTALL_FOLDER_NAME).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}

package org.gradle.profiler.studio.tools;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.studio.LaunchConfiguration;

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

    public void installPlugin(LaunchConfiguration configuration) {
        // Delete previous directory in case the plugin directory is reused
        File pluginsDir = configuration.getStudioPluginsDir().toFile();
        File pluginInstallDir = new File(pluginsDir, PLUGIN_INSTALL_FOLDER_NAME);
        deletePluginDirectory(pluginInstallDir);
        installPluginToDirectory(
            configuration.getStudioPluginsDir(),
            configuration.getStudioPluginJars()
        );
        registerDeletePluginShutdownHook(pluginInstallDir);
    }

    private void installPluginToDirectory(Path directory, List<Path> jars) {
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

    private void deletePluginDirectory(File pluginInstallDir) {
        try {
            FileUtils.deleteDirectory(pluginInstallDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void registerDeletePluginShutdownHook(File pluginInstallDir) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtils.deleteQuietly(pluginInstallDir)));
    }

}

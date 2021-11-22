package org.gradle.profiler.studio.tools;

<<<<<<< HEAD
import org.gradle.profiler.support.FileSupport;
=======
import com.google.common.annotations.VisibleForTesting;
>>>>>>> f5182f0 (Add support for Windows and Linux)

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class StudioSandboxCreator {

    /**
     * Creates sandbox for Android Studio.
     *
     * If sandboxDir is not specified only plugins and logs sandbox will be created, but config and system folder won't be.
     * In that case Android Studio will use configs from default folder, additionally it will write to default folder.
     */
    public static StudioSandbox createSandbox(@Nullable Path sandboxDir) {
        return createSandbox(sandboxDir, "plugins");
    }

    @VisibleForTesting
    public static StudioSandbox createSandbox(@Nullable Path sandboxDir, String pluginsDirName) {
        if (shouldCreatePartialSandbox(sandboxDir)) {
            Path path = newTempDir();
            Path pluginsDir = createDir(new File(path.toFile(), pluginsDirName).toPath());
            Path logsDir = createDir(new File(path.toFile(), "logs").toPath());
            return StudioSandbox.partialSandbox(pluginsDir, logsDir);
        }
        File sandboxDirFile = sandboxDir.toFile();
        Path configDir = createDir(new File(sandboxDirFile, "config").toPath());
        Path systemDir = createDir(new File(sandboxDirFile, "system").toPath());
        Path pluginsDir = createDir(new File(sandboxDirFile, pluginsDirName).toPath());
        Path logDir = createDir(new File(sandboxDirFile, "logs").toPath());
        disableIdeUpdate(configDir);
        return StudioSandbox.fullSandbox(configDir, systemDir, pluginsDir, logDir);
    }

    private static boolean shouldCreatePartialSandbox(Path sandboxDir) {
        return sandboxDir == null;
    }

    /**
     * Disables ide updates checks similar as it is done in gradle-intellij-plugin:
     * https://github.com/JetBrains/gradle-intellij-plugin/blob/719981bf5627ec8890f98e6f24c645e512a907a8/src/main/kotlin/org/jetbrains/intellij/tasks/PrepareSandboxTask.kt#L122
     */
    private static void disableIdeUpdate(Path configDir) {
        String updatesContent = "" +
            "<application>\n" +
            "  <component name=\"UpdatesConfigurable\">\n" +
            "    <option name=\"CHECK_NEEDED\" value=\"false\" />\n" +
            "  </component>\n" +
            "</application>\n";
        Path optionsDir = createDir(new File(configDir.toFile(), "options").toPath());
        File updatesXml = new File(optionsDir.toFile(), "updates.xml");
        if (!updatesXml.exists()) {
            FileSupport.writeUnchecked(updatesXml.toPath(), updatesContent);
        }
    }

    private static Path createDir(Path path) {
        new File(path.toAbsolutePath().toString()).mkdirs();
        return path.toAbsolutePath();
    }

    private static Path newTempDir() {
        try {
            return Files.createTempDirectory("android-studio-sandbox");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class StudioSandbox {

        private final Path configDir;
        private final Path systemDir;
        private final Path pluginsDir;
        private final Path logsDir;

        private StudioSandbox(@Nullable Path configDir, @Nullable Path systemDir, Path pluginsDir, Path logsDir) {
            this.configDir = configDir;
            this.systemDir = systemDir;
            this.pluginsDir = pluginsDir;
            this.logsDir = logsDir;
        }

        public Optional<Path> getConfigDir() {
            return Optional.ofNullable(configDir);
        }

        public Optional<Path> getSystemDir() {
            return Optional.ofNullable(systemDir);
        }

        public Path getLogsDir() {
            return logsDir;
        }

        public Path getPluginsDir() {
            return pluginsDir;
        }

        public static StudioSandbox fullSandbox(Path configDir, Path systemDir, Path pluginsDir, Path logsDir) {
            return new StudioSandbox(configDir, systemDir, pluginsDir, logsDir);
        }

        public static StudioSandbox partialSandbox(Path pluginsDir, Path logsDir) {
            return new StudioSandbox(null, null, pluginsDir, logsDir);
        }
    }
}

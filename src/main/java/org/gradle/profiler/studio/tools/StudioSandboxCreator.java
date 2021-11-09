package org.gradle.profiler.studio.tools;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class StudioSandboxCreator {

    private StudioSandboxCreator() {
    }

    /**
     * Creates sandbox for Android Studio.
     *
     * If sandboxDir is not specified only plugins and logs sandbox will be created, but config and system folder won't be.
     * In that case Android Studio will use configs from default folder, additionally it will write to default folder.
     */
    public static StudioSandbox createSandbox(@Nullable Path sandboxDir) {
        if (shouldCreatePartialSandbox(sandboxDir)) {
            Path path = newTempDir();
            Path pluginsDir = createDir(new File(path.toFile(), "plugins").toPath());
            Path logsDir = createDir(new File(path.toFile(), "logs").toPath());
            return StudioSandbox.partialSandbox(pluginsDir, logsDir);
        }
        File sandboxDirFile = sandboxDir.toFile();
        Path configDir = createDir(new File(sandboxDirFile, "config").toPath());
        Path systemDir = createDir(new File(sandboxDirFile, "system").toPath());
        Path pluginsDir = createDir(new File(sandboxDirFile, "plugins").toPath());
        Path logDir = createDir(new File(sandboxDirFile, "logs").toPath());
        return StudioSandbox.fullSandbox(configDir, systemDir, pluginsDir, logDir);
    }

    private static boolean shouldCreatePartialSandbox(Path sandboxDir) {
        return sandboxDir == null;
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

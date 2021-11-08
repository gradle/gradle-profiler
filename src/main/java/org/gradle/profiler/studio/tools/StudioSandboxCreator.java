package org.gradle.profiler.studio.tools;

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
     * Due to that Android Studio will use configs from default folder, additionally it will write to default folder.
     */
    public StudioSandbox createSandbox(Path sandboxDir) {
        if (shouldCreatePartialSandbox(sandboxDir)) {
            Path path = newTempDir();
            Path pluginsDir = createDir(new File(path.toFile(), "plugins").toPath());
            Path logDir = createDir(new File(path.toFile(), "logs").toPath());
            return new StudioSandbox(Optional.empty(), Optional.empty(), pluginsDir, logDir);
        }
        Path configDir = createDir(new File(sandboxDir.toFile(), "config").toPath());
        Path systemDir = createDir(new File(sandboxDir.toFile(), "system").toPath());
        Path pluginsDir = createDir(new File(sandboxDir.toFile(), "plugins").toPath());
        Path logDir = createDir(new File(sandboxDir.toFile(), "logs").toPath());
        return new StudioSandbox(Optional.of(configDir), Optional.of(systemDir), pluginsDir, logDir);
    }

    private boolean shouldCreatePartialSandbox(Path sandboxDir) {
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

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static class StudioSandbox {

        private final Optional<Path> configDir;
        private final Optional<Path> systemDir;
        private final Path pluginsDir;
        private final Path logsDir;

        public StudioSandbox(Optional<Path> configDir, Optional<Path> systemDir, Path pluginsDir, Path logsDir) {
            this.configDir = configDir;
            this.systemDir = systemDir;
            this.pluginsDir = pluginsDir;
            this.logsDir = logsDir;
        }

        public Optional<Path> getConfigDir() {
            return configDir;
        }

        public Optional<Path> getSystemDir() {
            return systemDir;
        }

        public Path getLogsDir() {
            return logsDir;
        }

        public Path getPluginsDir() {
            return pluginsDir;
        }
    }
}

package org.gradle.profiler.studio;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Optional;

/**
 * User-facing configuration for an IDE installation: where it is installed and which sandbox to use.
 */
public final class IdeConfiguration {

    private final File installDir;
    private final File sandboxDir;

    public IdeConfiguration(File installDir, @Nullable File sandboxDir) {
        this.installDir = installDir;
        this.sandboxDir = sandboxDir;
    }

    public File getInstallDir() {
        return installDir;
    }

    public Optional<File> getSandboxDir() {
        return Optional.ofNullable(sandboxDir);
    }
}

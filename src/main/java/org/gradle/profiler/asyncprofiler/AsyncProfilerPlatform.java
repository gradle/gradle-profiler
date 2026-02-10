package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.OperatingSystem;

import javax.annotation.Nullable;

public enum AsyncProfilerPlatform {

    LINUX_AARCH64("linux-arm64", "tar.gz", ".so"),
    LINUX_X64("linux-x64", "tar.gz", ".so"),
    MACOS("macos", "zip", ".dylib"),
    // WINDOWS is not natively supported, but WSL is detected as LINUX
    ;

    private final String platformName;
    private final String downloadExtension;
    private final String dynamicLibraryExtension;


    AsyncProfilerPlatform(String platformName, String downloadExtension, String dynamicLibraryExtension) {
        this.platformName = platformName;
        this.downloadExtension = downloadExtension;
        this.dynamicLibraryExtension = dynamicLibraryExtension;
    }

    public String getPlatformName() {
        return platformName;
    }

    public String getDownloadExtension() {
        return downloadExtension;
    }

    public String getDynamicLibraryExtension() {
        return dynamicLibraryExtension;
    }

    /**
     * Returns the platform for the current operating system, or {@code null} if unsupported.
     */
    @Nullable
    public static AsyncProfilerPlatform current() {
        if (OperatingSystem.isMacOS()) {
            return MACOS;
        } else if (OperatingSystem.isLinuxAarch64()) {
            return LINUX_AARCH64;
        } else if (OperatingSystem.isLinuxX86()) {
            return LINUX_X64;
        } else {
            return null;
        }
    }
}

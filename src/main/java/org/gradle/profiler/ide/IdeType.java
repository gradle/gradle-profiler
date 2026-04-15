package org.gradle.profiler.ide;

import com.google.common.collect.ImmutableList;
import org.gradle.profiler.OperatingSystem;

import java.util.List;

/**
 * Identifies which IntelliJ-based IDE is being profiled.
 * Used to validate the install directory and locate the right starter executable.
 */
public enum IdeType {
    INTELLIJ_IDEA(
        "IntelliJ IDEA",
        "IDEA_VM_OPTIONS",
        "IDEA_PROPERTIES",
        ImmutableList.of("Contents/MacOS/idea"),
        ImmutableList.of("bin/idea.bat"),
        ImmutableList.of("bin/idea.sh")
    ),
    ANDROID_STUDIO(
        "Android Studio",
        "STUDIO_VM_OPTIONS",
        "STUDIO_PROPERTIES",
        ImmutableList.of("Contents/MacOS/studio"),
        ImmutableList.of("bin/studio.bat"),
        ImmutableList.of("bin/studio.sh")
    );

    private final String displayName;
    private final String vmOptionsEnvVar;
    private final String propertiesEnvVar;
    private final List<String> macOsStarterPaths;
    private final List<String> windowsStarterPaths;
    private final List<String> linuxStarterPaths;

    IdeType(
        String displayName,
        String vmOptionsEnvVar,
        String propertiesEnvVar,
        List<String> macOsStarterPaths,
        List<String> windowsStarterPaths,
        List<String> linuxStarterPaths
    ) {
        this.displayName = displayName;
        this.vmOptionsEnvVar = vmOptionsEnvVar;
        this.propertiesEnvVar = propertiesEnvVar;
        this.macOsStarterPaths = macOsStarterPaths;
        this.windowsStarterPaths = windowsStarterPaths;
        this.linuxStarterPaths = linuxStarterPaths;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Name of the environment variable the IDE startup script reads to locate a custom
     * {@code .vmoptions} file.
     */
    public String getVmOptionsEnvVar() {
        return vmOptionsEnvVar;
    }

    /**
     * Name of the environment variable the IDE startup script reads to locate a custom
     * {@code idea.properties} file.
     */
    public String getPropertiesEnvVar() {
        return propertiesEnvVar;
    }

    /**
     * Relative paths within the IDE installation where the starter executable might be found,
     * for the current operating system.
     */
    public List<String> getStarterPathsForCurrentOs() {
        if (OperatingSystem.isMacOS()) {
            return macOsStarterPaths;
        } else if (OperatingSystem.isWindows()) {
            return windowsStarterPaths;
        } else {
            return linuxStarterPaths;
        }
    }
}

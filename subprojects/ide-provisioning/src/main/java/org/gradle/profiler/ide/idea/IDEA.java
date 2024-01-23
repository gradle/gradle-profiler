package org.gradle.profiler.ide.idea;

import org.gradle.profiler.ide.Ide;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IDEA implements Ide {
    public static IDEA LATEST_RELEASE = new IDEA("release", "");
    private final String version;
    private final String buildType;

    public IDEA(@Nullable String buildType, @NotNull String version) {
        this.version = version;
        this.buildType = buildType;
    }

    @NotNull
    @Override
    public String getVersion() {
        return version;
    }

    @NotNull
    public String getBuildType() {
        return buildType == null ? "release" : buildType;
    }
}

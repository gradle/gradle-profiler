package org.gradle.profiler.ide.studio;

import org.gradle.profiler.ide.Ide;
import org.jetbrains.annotations.NotNull;

public class AndroidStudio implements Ide {
    public static AndroidStudio LATEST = new AndroidStudio("");
    private final String version;

    public AndroidStudio(String version) {
        this.version = version;
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }
}

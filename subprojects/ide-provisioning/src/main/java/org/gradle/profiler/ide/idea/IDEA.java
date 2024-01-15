package org.gradle.profiler.ide.idea;

import org.gradle.profiler.ide.Ide;
import org.jetbrains.annotations.NotNull;

public class IDEA implements Ide {
    public static IDEA LATEST = new IDEA("");
    private final String version;
    public IDEA(String version) {
        this.version = version;
    }

    @NotNull
    @Override
    public String getVersion() {
        return version;
    }
}

package org.gradle.profiler.ide;

import org.jetbrains.annotations.NotNull;

public interface Ide {
    @NotNull
    String getVersion();
}


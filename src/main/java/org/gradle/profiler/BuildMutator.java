package org.gradle.profiler;

import java.io.IOException;

public interface BuildMutator {
    void beforeBuild() throws IOException;

    void cleanup() throws IOException;
}

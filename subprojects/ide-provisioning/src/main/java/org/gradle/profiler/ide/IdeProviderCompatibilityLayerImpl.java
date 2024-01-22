package org.gradle.profiler.ide;

import java.io.File;
import java.nio.file.Path;

public class IdeProviderCompatibilityLayerImpl {

    public File provideIde(String ideType, String ideVersion, Path homeDir, Path downloadsDir) {
        throw new RuntimeException("Must run with Java 17");
    }
}

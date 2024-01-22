package org.gradle.profiler.ide;

import java.io.File;
import java.nio.file.Path;

public interface IdeProvider<T extends Ide> {

    File provideIde(T ide, Path homeDir, Path downloadsDir);
}

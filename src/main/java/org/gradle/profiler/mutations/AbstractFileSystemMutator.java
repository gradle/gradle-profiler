package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

import java.io.File;

public class AbstractFileSystemMutator implements BuildMutator {
    protected static File resolveProjectFile(File projectDir, String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(projectDir, path);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}

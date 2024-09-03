package org.gradle.profiler.mutations;

import java.io.File;

public abstract class AbstractFileSystemMutator extends AbstractScheduledMutator {

    protected AbstractFileSystemMutator(Schedule schedule) {
        super(schedule);
    }

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

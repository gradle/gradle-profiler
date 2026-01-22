package org.gradle.profiler.mutations;

import java.io.File;

public abstract class AbstractFileSystemMutator extends AbstractScheduledMutator {

    protected AbstractFileSystemMutator(Schedule schedule) {
        super(schedule);
    }

    protected static File resolveProjectFile(File projectDir, String path) {
        return resolveFile(FileRoot.PROJECT, projectDir, null, path);
    }

    protected static File resolveFile(FileRoot root, File projectDir, File gradleUserHome, String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }

        File baseDir = root == FileRoot.GRADLE_USER_HOME ? gradleUserHome : projectDir;
        return new File(baseDir, path);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}

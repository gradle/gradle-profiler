package org.gradle.profiler.fixtures.file;

import java.io.File;

public class FileDeletionException extends RuntimeException {

    private final File file;

    public FileDeletionException(String message, File file) {
        super(message);
        this.file = file;
    }

    public File getFile() {
        return file;
    }
}

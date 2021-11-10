package org.gradle.profiler.mutations.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public class FileSupport {

    private FileSupport() {
    }

    public static void writeUnchecked(Path path, String text, OpenOption... options) {
        try {
            Files.write(path, text.getBytes(StandardCharsets.UTF_8), options);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}

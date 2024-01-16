package org.gradle.profiler.ide;

import org.jetbrains.annotations.Nullable;

import java.io.File;

public class UnpackUtils {

    @Nullable
    public static File getSingleFileFrom(File dir) {
        File[] unpackedFiles = dir.listFiles();
        if (unpackedFiles == null || unpackedFiles.length == 0) {
            return null;
        }
        if (unpackedFiles.length == 1) {
            return unpackedFiles[0];
        }
        throw new IllegalStateException("Unexpected content in " + dir);
    }
}

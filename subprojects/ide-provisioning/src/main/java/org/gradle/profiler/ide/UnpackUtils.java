package org.gradle.profiler.ide;

import java.io.File;

public class UnpackUtils {


    public static File getSingleFileFrom(File dir) {
        File[] unpackedFiles = dir.listFiles();
        if (unpackedFiles == null || unpackedFiles.length == 0) {
            throw new IllegalStateException("File is empty or not a directory");
        }
        if (unpackedFiles.length == 1) {
            return unpackedFiles[0];
        }
        throw new IllegalStateException("Unexpected content in " + dir);
    }

    public static boolean isDirNotEmpty(File file) {
        File[] dirFiles = file.listFiles();
        return file.exists() && dirFiles != null && dirFiles.length != 0;
    }
}

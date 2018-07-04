package org.gradle.profiler.mutations;

import java.io.File;

public abstract class AbstractKotlinSourceFileMutator extends AbstractFileChangeMutator {
    public AbstractKotlinSourceFileMutator(File sourceFile) {
        super(sourceFile);
        if (!sourceFile.getName().endsWith(".kt")) {
            throw new IllegalArgumentException("Can only modify Kotlin source files");
        }
    }
}

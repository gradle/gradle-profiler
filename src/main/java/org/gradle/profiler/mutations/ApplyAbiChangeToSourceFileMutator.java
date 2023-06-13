package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyAbiChangeToSourceFileMutator extends AbstractDelegateFileMutator {

    public ApplyAbiChangeToSourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected AbstractFileChangeMutator getFileChangeMutator(File sourceFile) {
        if (sourceFile.getName().endsWith(".kt")) {
            return new ApplyAbiChangeToKotlinSourceFileMutator(sourceFile);
        } else if (sourceFile.getName().endsWith(".java")) {
            return new ApplyAbiChangeToJavaSourceFileMutator(sourceFile);
        } else {
            throw new IllegalArgumentException("Can only modify Java or Kotlin source files");
        }
    }
}

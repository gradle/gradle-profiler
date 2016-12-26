package org.gradle.profiler.mutations;

import java.io.File;

public abstract class AbstractJavaSourceFileMutator extends AbstractFileChangeMutator {
    public AbstractJavaSourceFileMutator(File sourceFile) {
        super(sourceFile);
        if (!sourceFile.getName().endsWith(".java")) {
            throw new IllegalArgumentException("Can only modify Java source files");
        }
    }

    @Override
    protected void applyChangeTo(StringBuilder text) {
        int insertPos = text.lastIndexOf("}");
        if (insertPos < 0) {
            throw new IllegalArgumentException("Cannot parse source file " + sourceFile + " to apply changes");
        }
        applyChangeAt(text, insertPos);
    }

    protected abstract void applyChangeAt(StringBuilder text, int insertPos);
}

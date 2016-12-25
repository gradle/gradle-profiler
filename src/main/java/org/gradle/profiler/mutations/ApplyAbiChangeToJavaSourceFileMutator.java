package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyAbiChangeToJavaSourceFileMutator extends AbstractFileChangeMutator {
    public ApplyAbiChangeToJavaSourceFileMutator(File sourceFile) {
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
        text.insert(insertPos, "public void _m" + getUniqueText() + "() { }");
    }
}

package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyAbiChangeToJavaSourceFileMutator extends AbstractJavaSourceFileMutator {
    public ApplyAbiChangeToJavaSourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeAt(StringBuilder text, int insertPos) {
        text.insert(insertPos, "public void _m" + getUniqueText() + "() { }");
    }
}

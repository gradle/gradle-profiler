package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyNonAbiChangeToJavaSourceFileMutator extends AbstractJavaSourceFileMutator {
    public ApplyNonAbiChangeToJavaSourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeAt(StringBuilder text, int lastMethodEndPos) {
        text.insert(lastMethodEndPos, "System.out.println(\"" + getUniqueText() + "\");");
    }
}

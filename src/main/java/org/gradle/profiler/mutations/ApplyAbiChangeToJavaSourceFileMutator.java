package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyAbiChangeToJavaSourceFileMutator extends AbstractJavaSourceFileMutator {
    public ApplyAbiChangeToJavaSourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeAt(StringBuilder text, int lastMethodEndPos) {
        String method = "_m" + getUniqueText() + "()";
        text.insert(lastMethodEndPos + 1, "public void " + method + " { }");
        text.insert(lastMethodEndPos, method + ";");
    }
}

package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyNonAbiChangeToJavaSourceFileMutator extends AbstractJavaSourceFileMutator {
    protected ApplyNonAbiChangeToJavaSourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeAt(StringBuilder text, int insertPos) {
        text.insert(insertPos, "public void _m" + getInvocationText() + "() { System.out.println(\"" + getUniqueText() + "\"); }");
    }
}

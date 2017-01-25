package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyChangeToNativeSourceFileMutator extends AbstractFileChangeMutator {
    private static long classCreationTime = System.currentTimeMillis();

    public ApplyChangeToNativeSourceFileMutator(File file) {
        super(file);
        if (!sourceFile.getName().endsWith(".cpp") && !sourceFile.getName().endsWith(".h")) {
            throw new IllegalArgumentException("Can only modify C++ source or header files");
        }
    }

    @Override
    protected void applyChangeTo(StringBuilder text) {
        int insertPos;
        if (sourceFile.getName().endsWith(".cpp")) {
            insertPos = text.length();
            applyChangeAt(text, insertPos);
        } else {
            insertPos = text.lastIndexOf("#endif");
            if (insertPos < 0) {
                throw new IllegalArgumentException("Cannot parse header file " + sourceFile + " to apply changes");
            }
            applyHeaderChangeAt(text, insertPos);
        }
    }

    protected String getSharedUniqueText() {
        return "_" + String.valueOf(classCreationTime) + "_" + counter;
    }

    protected void applyChangeAt(StringBuilder text, int insertPos) {
        text.insert(insertPos, "\nint _m" + getSharedUniqueText() + " () { }");
    }

    protected void applyHeaderChangeAt(StringBuilder text, int insertPos) {
        text.insert(insertPos, "int _m" + getSharedUniqueText() + "();\n");
    }
}

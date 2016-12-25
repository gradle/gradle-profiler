package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyChangeToAndroidResourceFileMutator extends AbstractFileChangeMutator {
    public ApplyChangeToAndroidResourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeTo(StringBuilder text) {
        int insertPos = text.lastIndexOf("</resources>");
        if (insertPos < 0) {
            throw new IllegalArgumentException("Cannot parse source file " + sourceFile + " to apply changes");
        }
        text.insert(insertPos, "<string name=\"new_resource\">" + getUniqueText() + "</string>");
    }
}

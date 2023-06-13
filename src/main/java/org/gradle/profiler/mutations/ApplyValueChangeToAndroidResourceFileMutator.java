package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;

import java.io.File;

public class ApplyValueChangeToAndroidResourceFileMutator extends AbstractFileChangeMutator {
    public ApplyValueChangeToAndroidResourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        int insertPos = text.lastIndexOf("</string>");
        if (insertPos < 0) {
            throw new IllegalArgumentException("Cannot parse source file " + sourceFile + " to apply changes");
        }
        text.insert(insertPos, context.getUniqueBuildId());
    }
}

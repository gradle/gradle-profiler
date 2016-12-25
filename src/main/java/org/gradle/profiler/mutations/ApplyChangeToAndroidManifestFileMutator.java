package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyChangeToAndroidManifestFileMutator extends AbstractFileChangeMutator {
    public ApplyChangeToAndroidManifestFileMutator(File sourceFile) {
        super( sourceFile );
    }

    @Override
    protected void applyChangeTo(StringBuilder text) {
        int insertPos = text.lastIndexOf("</manifest>");
        if (insertPos < 0) {
            throw new IllegalArgumentException("Cannot parse android manifest file " + sourceFile + " to apply changes");
        }
        text.insert(insertPos, "<!-- " + getUniqueText() + " --!><permission android:name=\"com.acme.SOME_PERMISSION\"/>");
    }
}

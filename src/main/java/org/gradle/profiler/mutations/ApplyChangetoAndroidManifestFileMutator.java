package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyChangetoAndroidManifestFileMutator extends AbstractFileChangeMutator {
    public ApplyChangetoAndroidManifestFileMutator(File sourceFile) {
        super( sourceFile );
    }

    @Override
    protected void applyChangeTo(StringBuilder text) {
        int insertPos = text.lastIndexOf("</manifest>");
        if (insertPos < 0) {
            throw new IllegalArgumentException("Cannot parse android manifest file " + sourceFile + " to apply changes");
        }
        text.insert(insertPos, "<permission android:name=\"com.acme.SOME_PERMISSION\"/>");
    }
}

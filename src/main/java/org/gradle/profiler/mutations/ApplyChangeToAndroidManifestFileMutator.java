package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;

import java.io.File;

public class ApplyChangeToAndroidManifestFileMutator extends AbstractFileChangeMutator {
    public ApplyChangeToAndroidManifestFileMutator(File sourceFile) {
        super( sourceFile );
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        int insertPos = text.lastIndexOf("</manifest>");
        if (insertPos < 0) {
            throw new IllegalArgumentException("Cannot parse android manifest file " + sourceFile + " to apply changes");
        }
        text.insert(insertPos, "<!-- " + context.getUniqueBuildId() + " --><permission android:name=\"com.acme.SOME_PERMISSION\"/>");
    }
}

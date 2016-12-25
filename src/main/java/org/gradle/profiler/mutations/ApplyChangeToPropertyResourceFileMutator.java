package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyChangeToPropertyResourceFileMutator extends AbstractFileChangeMutator {
    public ApplyChangeToPropertyResourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeTo(StringBuilder text) {
        text.append("\norg.acme.some=").append(getUniqueText()).append("\n");
    }
}

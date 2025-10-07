package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;

import java.io.File;

public class ApplyChangeToPropertyResourceFileMutator extends AbstractFileChangeMutator {
    public ApplyChangeToPropertyResourceFileMutator(File sourceFile) {
        super(sourceFile, "property file change");
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        text.append("\norg.acme.some=").append(context.getUniqueBuildId()).append("\n");
    }
}

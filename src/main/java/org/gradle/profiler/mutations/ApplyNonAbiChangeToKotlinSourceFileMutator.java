package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;

import java.io.File;

public class ApplyNonAbiChangeToKotlinSourceFileMutator extends AbstractKotlinSourceFileMutator {

    public ApplyNonAbiChangeToKotlinSourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        text.append("private fun _m")
                .append(context.getUniqueBuildId())
                .append("() {}");
    }
}

package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;

import java.io.File;

public class ApplyAbiChangeToKotlinSourceFileMutator extends AbstractKotlinSourceFileMutator {

    public ApplyAbiChangeToKotlinSourceFileMutator(File sourceFile) {
        super(sourceFile, "ABI change");
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        text.append("\n\n")
            .append("fun _m")
            .append(context.getUniqueBuildId())
            .append("() {}");
    }
}

package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyNonAbiChangeToKotlinSourceFileMutator extends AbstractKotlinSourceFileMutator {

    public ApplyNonAbiChangeToKotlinSourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeTo(StringBuilder text) {
        text.append("private fun _m")
                .append(getUniqueText())
                .append("() {}");
    }
}

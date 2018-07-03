package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyAbiChangeToKotlinSourceFileMutator extends AbstractKotlinSourceFileMutator {

    public ApplyAbiChangeToKotlinSourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeTo(StringBuilder text) {
        text.append("fun _m")
                .append(getUniqueText())
                .append("() {}");
    }
}

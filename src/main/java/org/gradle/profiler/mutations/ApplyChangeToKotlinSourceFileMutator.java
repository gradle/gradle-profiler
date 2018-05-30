package org.gradle.profiler.mutations;

import java.io.File;

public class ApplyChangeToKotlinSourceFileMutator extends AbstractFileChangeMutator {
    public ApplyChangeToKotlinSourceFileMutator(File sourceFile) {
        super(sourceFile);
        if (!sourceFile.getName().endsWith(".kt")) {
            throw new IllegalArgumentException("Can only modify Kotlin source files");
        }
    }

    @Override
    protected void applyChangeTo(StringBuilder text) {
        text.append("fun _m")
                .append(getUniqueText())
                .append("() {}");
    }
}

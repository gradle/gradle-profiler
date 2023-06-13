package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;

import java.io.File;

public class ApplyBuildScriptChangeFileMutator extends AbstractFileChangeMutator {
    public ApplyBuildScriptChangeFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        text.append("\n");
        text.append("println(\"");
        text.append(context.getUniqueBuildId());
        text.append("\")\n");
    }
}

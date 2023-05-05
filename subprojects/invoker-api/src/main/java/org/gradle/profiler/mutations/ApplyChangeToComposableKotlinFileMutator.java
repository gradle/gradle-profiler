package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;

import java.io.File;

/**
 * This class applies a mutation to a Kotlin file by adding an extra composable function, indicating
 * an ABI change within the UI layer.
 */
public class ApplyChangeToComposableKotlinFileMutator extends AbstractKotlinSourceFileMutator {

    public ApplyChangeToComposableKotlinFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        text.append("@androidx.compose.runtime.Composable fun M")
                .append(context.getUniqueBuildId())
                .append("() {}\n");
    }
}

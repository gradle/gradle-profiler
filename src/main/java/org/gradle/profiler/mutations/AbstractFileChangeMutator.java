package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public abstract class AbstractFileChangeMutator implements BuildMutator {
    protected final File sourceFile;
    private final String originalText;

    protected AbstractFileChangeMutator(File sourceFile) {
        this.sourceFile = sourceFile;
        try {
            originalText = new String(Files.readAllBytes(sourceFile.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("Could not read contents of source file " + sourceFile, e);
        }
    }

    @Override
    public void beforeBuild(BuildContext context) {
        StringBuilder modifiedText = new StringBuilder(originalText);
        applyChangeTo(context, modifiedText);
        try {
            Files.write(sourceFile.toPath(), modifiedText.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract void applyChangeTo(BuildContext context, StringBuilder text);

    private void revert() {
        try {
            Files.write(sourceFile.toPath(), originalText.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterScenario(ScenarioContext context) {
        revert();
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + '(' + sourceFile + ')';
    }
}

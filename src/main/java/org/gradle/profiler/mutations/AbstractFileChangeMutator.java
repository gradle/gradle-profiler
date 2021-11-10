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
        this.originalText = readText(sourceFile);
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

    protected String readText(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("Could not read contents of source file " + sourceFile, e);
        }
    }

    protected abstract void applyChangeTo(BuildContext context, StringBuilder text);

    @Override
    public void afterScenario(ScenarioContext context) {
        revert(sourceFile, originalText);
    }

    protected void revert(File file, String originalText) {
        try {
            Files.write(file.toPath(), originalText.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + '(' + sourceFile + ')';
    }
}

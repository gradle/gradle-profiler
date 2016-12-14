package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public abstract class AbstractFileChangeMutator implements BuildMutator {
    protected final File sourceFile;
    private final String originalText;
    private StringBuilder modifiedText;
    private boolean modified;

    protected AbstractFileChangeMutator(File sourceFile) {
        this.sourceFile = sourceFile;
        try {
            originalText = new String(Files.readAllBytes(sourceFile.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("Could not read contents of source file " + sourceFile, e);
        }
    }

    @Override
    public void beforeBuild() throws IOException {
        if (modified) {
            revert();
        } else {
            if (modifiedText == null) {
                modifiedText = new StringBuilder(originalText);
                applyChangeTo(modifiedText);
            }
            Files.write(sourceFile.toPath(), modifiedText.toString().getBytes());
        }
        modified = !modified;
    }

    protected abstract void applyChangeTo(StringBuilder text);

    private void revert() throws IOException {
        Files.write(sourceFile.toPath(), originalText.getBytes());
    }

    @Override
    public void cleanup() throws IOException {
        if (modified) {
            revert();
            modified = false;
        }
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + '(' + sourceFile + ')';
    }
}

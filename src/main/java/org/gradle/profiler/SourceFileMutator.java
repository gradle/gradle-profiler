package org.gradle.profiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SourceFileMutator implements BuildMutator {
    private final File sourceFile;
    private final String originalText;
    private final StringBuilder modifiedText;
    private boolean modified;

    public SourceFileMutator(File sourceFile) throws IOException {
        if (!sourceFile.getName().endsWith(".java")) {
            throw new IllegalArgumentException("Can only modify Java source files");
        }
        this.sourceFile = sourceFile;
        originalText = new String(Files.readAllBytes(sourceFile.toPath()));
        int insertPos = originalText.lastIndexOf("}");
        if (insertPos < 0) {
            throw new IllegalArgumentException("Cannot parse source file " + sourceFile + " to apply changes");
        }
        modifiedText = new StringBuilder(originalText);
        modifiedText.insert(insertPos, "public void __new_method__() { }");
    }

    @Override
    public void beforeBuild() throws IOException {
        if (modified) {
            revert();
        } else {
            Files.write(sourceFile.toPath(), modifiedText.toString().getBytes());
        }
        modified = !modified;
    }

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
}

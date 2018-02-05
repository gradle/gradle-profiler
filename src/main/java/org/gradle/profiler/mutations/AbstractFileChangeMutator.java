package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public abstract class AbstractFileChangeMutator implements BuildMutator {
    protected final File sourceFile;
    private String originalText;
    private long timestamp;
    protected int counter;

    protected AbstractFileChangeMutator(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    @Override
    public void beforeScenario() throws IOException {
        this.timestamp = System.currentTimeMillis();
        try {
            originalText = new String(Files.readAllBytes(sourceFile.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("Could not read contents of source file " + sourceFile, e);
        }
    }

    void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns some text that is specific to the current profiler tool invocation and is unlikely to have been included in the target source file prior to this invocation
     * The string can be used as a Java identifier.
     */
    protected String getInvocationText() {
        return "_" + String.valueOf(timestamp);
    }

    /**
     * Returns some text that is unlikely to have been included in any previous version of the target source file.
     * The string can be used as a Java identifier.
     */
    protected String getUniqueText() {
        return "_" + String.valueOf(timestamp) + "_" + counter;
    }

    @Override
    public void beforeBuild() throws IOException {
        counter++;
        StringBuilder modifiedText = new StringBuilder(originalText);
        applyChangeTo(modifiedText);
        Files.write(sourceFile.toPath(), modifiedText.toString().getBytes());
    }

    protected abstract void applyChangeTo(StringBuilder text);

    private void revert() throws IOException {
        Files.write(sourceFile.toPath(), originalText.getBytes());
    }

    @Override
    public void afterScenario() throws IOException {
        if (counter > 0) {
            revert();
            counter = 0;
        }
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + '(' + sourceFile + ')';
    }
}

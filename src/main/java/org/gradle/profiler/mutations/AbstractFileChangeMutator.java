package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ScenarioContext;

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
        this.timestamp = System.currentTimeMillis();
        try {
            originalText = new String(Files.readAllBytes(sourceFile.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("Could not read contents of source file " + sourceFile, e);
        }
    }

    protected void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns some text that is unlikely to have been included in any previous version of the target source file.
     * The string can be used as a Java identifier.
     */
    protected String getUniqueText() {
        return "_" + String.valueOf(timestamp) + "_" + counter;
    }

    @Override
    public void beforeBuild(BuildContext context) {
        counter++;
        StringBuilder modifiedText = new StringBuilder(originalText);
        applyChangeTo(modifiedText);
        try {
            Files.write(sourceFile.toPath(), modifiedText.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract void applyChangeTo(StringBuilder text);

    private void revert() {
        try {
            Files.write(sourceFile.toPath(), originalText.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterScenario(ScenarioContext context) {
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

package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ScenarioContext;
import org.gradle.profiler.mutations.support.FileSupport;

import java.io.File;

public abstract class AbstractFileChangeMutator implements BuildMutator {
    protected final File sourceFile;
    private String originalText;

    protected AbstractFileChangeMutator(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        this.originalText = readText(sourceFile);
    }

    @Override
    public void beforeBuild(BuildContext context) {
        StringBuilder modifiedText = new StringBuilder(originalText);
        applyChangeTo(context, modifiedText);
        FileSupport.writeUnchecked(sourceFile.toPath(), modifiedText.toString());
    }

    private String readText(File file) {
        return FileSupport.readUnchecked(file.toPath());
    }

    protected abstract void applyChangeTo(BuildContext context, StringBuilder text);

    @Override
    public void afterScenario(ScenarioContext context) {
        revert(sourceFile, originalText);
    }

    protected void revert(File file, String originalText) {
        FileSupport.writeUnchecked(file.toPath(), originalText);
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + '(' + sourceFile + ')';
    }
}

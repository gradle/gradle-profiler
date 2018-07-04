package org.gradle.profiler.mutations;

import java.io.File;

public abstract class AbstractDelegateFileMutator extends AbstractFileChangeMutator {
    private final AbstractFileChangeMutator fileChangeMutator;

    AbstractDelegateFileMutator(File sourceFile) {
        super(sourceFile);
        fileChangeMutator = getFileChangeMutator(sourceFile);
    }

    protected abstract AbstractFileChangeMutator getFileChangeMutator(File sourceFile);

    @Override
    protected void applyChangeTo(StringBuilder text) {
        fileChangeMutator.applyChangeTo(text);
    }

    @Override
    public void beforeScenario() {
        fileChangeMutator.beforeScenario();
    }

    @Override
    public void beforeCleanup() {
        fileChangeMutator.beforeCleanup();
    }

    @Override
    public void afterCleanup(Throwable error) {
        fileChangeMutator.afterCleanup(error);
    }

    @Override
    public void beforeBuild() {
        fileChangeMutator.beforeBuild();
    }

    @Override
    public void afterBuild(Throwable error) {
        fileChangeMutator.afterBuild(error);
    }

    @Override
    public void afterScenario() {
        fileChangeMutator.afterScenario();
    }

    @Override
    protected void setTimestamp(long timestamp) {
        fileChangeMutator.setTimestamp(timestamp);
    }
}

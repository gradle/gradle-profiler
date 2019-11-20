package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;
import org.gradle.profiler.ScenarioContext;

import java.io.File;

public abstract class AbstractDelegateFileMutator extends AbstractFileChangeMutator {
    private final AbstractFileChangeMutator fileChangeMutator;

    AbstractDelegateFileMutator(File sourceFile) {
        super(sourceFile);
        fileChangeMutator = getFileChangeMutator(sourceFile);
    }

    protected abstract AbstractFileChangeMutator getFileChangeMutator(File sourceFile);

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        fileChangeMutator.applyChangeTo(context, text);
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        fileChangeMutator.beforeScenario(context);
    }

    @Override
    public void beforeCleanup(BuildContext context) {
        fileChangeMutator.beforeCleanup(context);
    }

    @Override
    public void afterCleanup(BuildContext context, Throwable error) {
        fileChangeMutator.afterCleanup(context, error);
    }

    @Override
    public void beforeBuild(BuildContext context) {
        fileChangeMutator.beforeBuild(context);
    }

    @Override
    public void afterBuild(BuildContext context, Throwable error) {
        fileChangeMutator.afterBuild(context, error);
    }

    @Override
    public void afterScenario(ScenarioContext context) {
        fileChangeMutator.afterScenario(context);
    }
}

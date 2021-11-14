package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.GradleBuildInvoker;
import org.gradle.profiler.mutations.support.ProjectCombinations;

import java.io.File;
import java.util.Set;

public class ApplyProjectDependencyChangeMutator extends AbstractFileChangeMutator {

    private final ProjectCombinations projectCombinations;

    protected ApplyProjectDependencyChangeMutator(File sourceFile, ProjectCombinations projectCombinations) {
        super(sourceFile);
        this.projectCombinations = projectCombinations;
    }

    @Override
    public void validate(BuildInvoker invoker) {
        if (!(invoker instanceof GradleBuildInvoker)) {
            throw new IllegalStateException("Only Gradle invoker is supported for " + this + ", but " + invoker + " was provided.");
        }
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        text.append("\ndependencies {\n");
        Set<String> projects = projectCombinations.getNextCombination();
        projects.forEach(it -> text.append("    project(\":").append(it).append("\")\n"));
        text.append("}");
    }

}

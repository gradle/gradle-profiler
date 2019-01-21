package org.gradle.profiler;

import org.gradle.tooling.ProjectConnection;

import java.util.List;

public class ToolingApiInvoker extends BuildInvoker {
    private final ProjectConnection projectConnection;

    public ToolingApiInvoker(ProjectConnection projectConnection) {
        this.projectConnection = projectConnection;
    }

    @Override
    public void run(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs, Class<?> toolingModel) {
        if (toolingModel == null) {
            run(projectConnection.newBuild(), build -> {
                build.forTasks(tasks.toArray(new String[0]));
                build.withArguments(gradleArgs);
                build.setJvmArguments(jvmArgs);
                build.run();
                return null;
            });
        } else {
            run(projectConnection.model(toolingModel), build -> {
                build.forTasks(tasks.toArray(new String[0]));
                build.withArguments(gradleArgs);
                build.setJvmArguments(jvmArgs);
                build.get();
                return null;
            });
        }
    }
}


package org.gradle.trace.listener;

import static org.gradle.trace.util.ReflectionUtil.invokerGetter;

import org.gradle.api.internal.TaskInternal;
import org.gradle.trace.TraceResult;

public class Gradle40BuildOperationListenerInvocationHandler extends BuildOperationListenerInvocationHandler {

    public Gradle40BuildOperationListenerInvocationHandler(TraceResult traceResult) {
        super(traceResult);
    }

    protected String getName(Object operation) {
        TaskInternal task = getTask(operation);
        if (task == null) {
            return invokerGetter(operation, "getDisplayName") + " (" + invokerGetter(operation, "getId") + ")";
        } else {
            return task.getPath();
        }
    }

    protected TaskInternal getTask(Object operation) {
        Object details = invokerGetter(operation, "getDetails");
        if (details != null && details.getClass().getName().equals("org.gradle.api.execution.internal.ExecuteTaskBuildOperationDetails")) {
            return (TaskInternal) invokerGetter(details, "getTask");
        }
        return null;
    }

    protected boolean isTaskCacheable(TaskInternal task, Object finishedEvent) {
        return (boolean) invokerGetter(invokerGetter(task.getState(), "getTaskOutputCaching"), "isEnabled");
    }
}

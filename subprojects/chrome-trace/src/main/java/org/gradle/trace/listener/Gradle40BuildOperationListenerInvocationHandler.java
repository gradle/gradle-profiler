package org.gradle.trace.listener;

import org.gradle.api.internal.TaskInternal;
import org.gradle.trace.TraceResult;

public class Gradle40BuildOperationListenerInvocationHandler extends BuildOperationListenerInvocationHandler {

    public Gradle40BuildOperationListenerInvocationHandler(TraceResult traceResult) {
        super(traceResult);
    }

    protected String getName(Object operation) {
        TaskInternal task = getTask(operation);
        if (task == null) {
            return call(operation, "getDisplayName") + " (" + call(operation, "getId") + ")";
        } else {
            return task.getPath();
        }
    }

    protected TaskInternal getTask(Object operation) {
        Object details = call(operation, "getDetails");
        if (details.getClass().getName().equals("org.gradle.api.execution.internal.ExecuteTaskBuildOperationDetails")) {
            return (TaskInternal) call(details, "getTask");
        }
        return null;
    }

    protected boolean isTaskCacheable(TaskInternal task) {
        return task.getState().getTaskOutputCaching().isEnabled();
    }
}

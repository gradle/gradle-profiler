package org.gradle.trace.listener;

import org.gradle.api.internal.TaskInternal;
import org.gradle.trace.TraceResult;

public class Gradle33BuildOperationListenerInvocationHandler extends BuildOperationListenerInvocationHandler {

    public Gradle33BuildOperationListenerInvocationHandler(TraceResult traceResult) {
        super(traceResult);
    }

    protected String getName(Object operation) {
        Object details = call(operation, "getOperationDescriptor");
        if (details != null && details.getClass().getSimpleName().equals("TaskOperationDescriptor")) {
            return (String) call(call(details, "getTask"), "getPath");
        }
        return call(operation, "getDisplayName") + " (" + call(operation, "getId") + ")";
    }

    protected TaskInternal getTask(Object operation) {
        Object details = call(operation, "getOperationDescriptor");
        if (details != null && details.getClass().getSimpleName().equals("TaskOperationDescriptor")) {
            return  (TaskInternal) call(details, "getTask");
        }
        return null;
    }

    protected boolean isTaskCacheable(TaskInternal task) {
        return (boolean) call(task.getState(), "isCacheable");
    }
}

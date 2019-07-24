package org.gradle.trace.listener;

import static org.gradle.trace.util.ReflectionUtil.invokerGetter;

import org.gradle.api.internal.TaskInternal;
import org.gradle.trace.TraceResult;

public class Gradle33BuildOperationListenerInvocationHandler extends BuildOperationListenerInvocationHandler {

    public Gradle33BuildOperationListenerInvocationHandler(TraceResult traceResult) {
        super(traceResult);
    }

    protected String getName(Object operation) {
        Object details = invokerGetter(operation, "getOperationDescriptor");
        if (details != null && details.getClass().getSimpleName().equals("TaskOperationDescriptor")) {
            return (String) invokerGetter(invokerGetter(details, "getTask"), "getPath");
        }
        return invokerGetter(operation, "getDisplayName") + " (" + invokerGetter(operation, "getId") + ")";
    }

    protected TaskInternal getTask(Object operation) {
        Object details = invokerGetter(operation, "getOperationDescriptor");
        if (details != null && details.getClass().getSimpleName().equals("TaskOperationDescriptor")) {
            return  (TaskInternal) invokerGetter(details, "getTask");
        }
        return null;
    }

    protected boolean isTaskCacheable(TaskInternal task, Object finishedEvent) {
        return (boolean) invokerGetter(task.getState(), "isCacheable");
    }
}

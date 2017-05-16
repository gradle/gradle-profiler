package org.gradle.trace.listener;

import org.gradle.api.execution.internal.TaskOperationDetails;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.trace.TraceResult;

public class Gradle40BuildOperationListenerInvocationHandler extends BuildOperationListenerInvocationHandler {

    public Gradle40BuildOperationListenerInvocationHandler(TraceResult traceResult) {
        super(traceResult);
    }

    protected String getName(Object operation) {
        BuildOperationDescriptor operationDescriptor = (BuildOperationDescriptor) operation;
        if (operationDescriptor.getDetails() instanceof TaskOperationDetails) {
            return ((TaskOperationDetails) operationDescriptor.getDetails()).getTask().getPath();
        }
        return operationDescriptor.getDisplayName() + " (" + operationDescriptor.getId() + ")";
    }

    protected TaskInternal getTask(Object operation) {
        BuildOperationDescriptor operationDescriptor = (BuildOperationDescriptor) operation;
        if (operationDescriptor.getDetails() instanceof TaskOperationDetails) {
            TaskOperationDetails taskDescriptor = (TaskOperationDetails) operationDescriptor.getDetails();
            return taskDescriptor.getTask();
        }
        return null;
    }
}

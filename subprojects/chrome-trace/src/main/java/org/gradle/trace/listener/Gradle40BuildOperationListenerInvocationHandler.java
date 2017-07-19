package org.gradle.trace.listener;

import org.gradle.api.execution.internal.ExecuteTaskBuildOperationDetails;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.trace.TraceResult;

public class Gradle40BuildOperationListenerInvocationHandler extends BuildOperationListenerInvocationHandler {

    public Gradle40BuildOperationListenerInvocationHandler(TraceResult traceResult) {
        super(traceResult);
    }

    protected String getName(Object operation) {
        BuildOperationDescriptor operationDescriptor = (BuildOperationDescriptor) operation;
        if (operationDescriptor.getDetails() instanceof ExecuteTaskBuildOperationDetails) {
            return ((ExecuteTaskBuildOperationDetails) operationDescriptor.getDetails()).getTask().getPath();
        }
        return operationDescriptor.getDisplayName() + " (" + operationDescriptor.getId() + ")";
    }

    protected TaskInternal getTask(Object operation) {
        BuildOperationDescriptor operationDescriptor = (BuildOperationDescriptor) operation;
        if (operationDescriptor.getDetails() instanceof ExecuteTaskBuildOperationDetails) {
            ExecuteTaskBuildOperationDetails taskDescriptor = (ExecuteTaskBuildOperationDetails) operationDescriptor.getDetails();
            return taskDescriptor.getTask();
        }
        return null;
    }

    protected boolean isTaskCacheable(TaskInternal task) {
        return task.getState().getTaskOutputCaching().isEnabled();
    }
}

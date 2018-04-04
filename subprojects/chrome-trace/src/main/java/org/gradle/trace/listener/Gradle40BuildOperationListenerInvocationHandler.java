package org.gradle.trace.listener;

import org.gradle.api.execution.internal.ExecuteTaskBuildOperationDetails;
import org.gradle.api.internal.TaskInternal;
import org.gradle.trace.TraceResult;

public class Gradle40BuildOperationListenerInvocationHandler extends BuildOperationListenerInvocationHandler {

    public Gradle40BuildOperationListenerInvocationHandler(TraceResult traceResult) {
        super(traceResult);
    }

    protected String getName(Object operation) {
        Object details = call(operation, "getDetails");
        if (details instanceof ExecuteTaskBuildOperationDetails) {
            return ((ExecuteTaskBuildOperationDetails) details).getTask().getPath();
        }
        return call(operation, "getDisplayName") + " (" + call(operation, "getId") + ")";
    }

    protected TaskInternal getTask(Object operation) {
        Object details = call(operation, "getDetails");
        if (details instanceof ExecuteTaskBuildOperationDetails) {
            ExecuteTaskBuildOperationDetails taskDescriptor = (ExecuteTaskBuildOperationDetails) details;
            return taskDescriptor.getTask();
        }
        return null;
    }

    protected boolean isTaskCacheable(TaskInternal task) {
        return task.getState().getTaskOutputCaching().isEnabled();
    }
}

package org.gradle.trace.listener;

import static org.gradle.trace.util.ReflectionUtil.invokerGetter;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.trace.TraceResult;

public class Gradle50BuildOperationListenerInvocationHandler extends BuildOperationListenerInvocationHandler {

    public Gradle50BuildOperationListenerInvocationHandler(TraceResult traceResult) {
        super(traceResult);
    }

    protected String getName(Object operation) {
        BuildOperationDescriptor operationDescriptor = (BuildOperationDescriptor) operation;
        TaskInternal task = getTask(operation);
        if (task == null) {
            return operationDescriptor.getDisplayName() + " (" + operationDescriptor.getId() + ")";
        } else {
            return task.getPath();
        }
    }

    protected TaskInternal getTask(Object operation) {
        Object details = invokerGetter(operation, "getDetails");
        if (details instanceof ExecuteTaskBuildOperationDetails) {
            return ((ExecuteTaskBuildOperationDetails) details).getTask();
        }
        return null;
    }

    @Override
    protected long getStartTime(Object startEvent) {
        return ((OperationStartEvent) startEvent).getStartTime();
    }

    @Override
    protected long getEndTime(Object result) {
        return ((OperationFinishEvent) result).getEndTime();
    }

    @Override
    protected boolean isTaskCacheable(TaskInternal task, Object finishedEvent) {
        Object result = ((OperationFinishEvent) finishedEvent).getResult();
        if (result instanceof ExecuteTaskBuildOperationType.Result) {
            return ((ExecuteTaskBuildOperationType.Result) result).getCachingDisabledReasonCategory() == null;
        }
        throw new UnsupportedOperationException();
    }
}

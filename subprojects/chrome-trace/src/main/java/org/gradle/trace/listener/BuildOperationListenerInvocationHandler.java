package org.gradle.trace.listener;

import org.gradle.api.internal.TaskInternal;
import org.gradle.trace.TraceResult;
import org.gradle.trace.util.TimeUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public abstract class BuildOperationListenerInvocationHandler implements InvocationHandler {
    private static final String CATEGORY_OPERATION = "BUILD_OPERATION";

    private TraceResult traceResult;

    public BuildOperationListenerInvocationHandler(TraceResult traceResult) {
        this.traceResult = traceResult;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        switch (method.getName()) {
            case "started": {
                Object operation = args[0];
                Object startEvent = args[1];
                traceResult.start(getName(operation), CATEGORY_OPERATION, TimeUtil.toNanoTime(getStartTime(startEvent)));
                return null;
            }
            case "finished": {
                Object operation = args[0];
                Object result = args[1];
                Map<String, String> info = new HashMap<>();
                withTaskInfo(info, getTask(operation));
                traceResult.finish(getName(operation), TimeUtil.toNanoTime(getEndTime(result)), info);
                return null;
            }
            case "hashCode":
                return hashCode();
            case "equals":
                return isEqual(proxy, args[0]);
            case "toString":
                return toString();
        }
        return null;
    }

    private Object isEqual(Object me, Object other) {
        if (me == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (me.getClass() != other.getClass()) {
            return false;
        }
        return equals(Proxy.getInvocationHandler(other));
    }

    protected long getStartTime(Object startEvent) {
        return (long) call(startEvent, "getStartTime");
    }

    protected long getEndTime(Object result) {
        return (long) call(result, "getEndTime");
    }

    protected abstract String getName(Object operation);

    protected abstract TaskInternal getTask(Object operation);

    private void withTaskInfo(Map<String, String> info, TaskInternal task) {
        if (task == null) {
            return;
        }
        info.put("type", task.getClass().getSimpleName().replace("_Decorated", ""));
        info.put("enabled", String.valueOf(task.getEnabled()));
        info.put("cacheable", String.valueOf(isTaskCacheable(task)));
        info.put("parallelizeable", String.valueOf(false));
        info.put("outcome", task.getState().getOutcome().name());
    }

    protected abstract boolean isTaskCacheable(TaskInternal task);

    protected Object call(Object object, String method) {
        try {
            Method methodHandle = object.getClass().getMethod(method);
            methodHandle.setAccessible(true);
            return methodHandle.invoke(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

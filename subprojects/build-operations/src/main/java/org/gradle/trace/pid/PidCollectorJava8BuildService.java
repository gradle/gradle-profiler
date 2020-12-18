package org.gradle.trace.pid;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.InvocationTargetException;

public abstract class PidCollectorJava8BuildService extends AbstractPidCollectorBuildService {

    @Override
    protected Long getPid() {
        try {
            // With Gradle 6.6 we can't inject `ProcessEnvironment`, so that is why we access the runtime MXBean here.
            // On Java 9 we use `ProcessHandle` via `PidCollectorBuildService`.
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            java.lang.reflect.Field jvm = runtime.getClass().getDeclaredField("jvm");
            jvm.setAccessible(true);
            sun.management.VMManagement mgmt =
                (sun.management.VMManagement) jvm.get(runtime);
            java.lang.reflect.Method pid_method =
                mgmt.getClass().getDeclaredMethod("getProcessId");
            pid_method.setAccessible(true);

            return ((Integer) pid_method.invoke(mgmt)).longValue();
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}

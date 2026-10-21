package org.gradle.trace.pid;

import org.gradle.api.JavaVersion;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.InvocationTargetException;

/**
 * Resolves the PID of the current process.
 *
 * <p>Build services cannot inject {@code ProcessEnvironment} since Gradle 6.6. Resolve the PID when the build service runs rather than when
 * it is registered, as the configuration cache restores the parameters of a build service in later build processes.</p>
 */
public class CurrentProcess {
    public static long getPid() {
        if (JavaVersion.current().isJava9Compatible()) {
            // `ProcessHandle` is only available since Java 9, so it is referenced from a separate class
            return Java9CurrentProcess.getPid();
        }
        try {
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

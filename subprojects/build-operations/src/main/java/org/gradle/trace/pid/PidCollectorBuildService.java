package org.gradle.trace.pid;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.InvocationTargetException;

public abstract class PidCollectorBuildService implements BuildService<PidCollectorBuildService.Parameters>, OperationCompletionListener {

    public static void registerBuildService(GradleInternal gradle, File outFile) {
        Provider<PidCollectorBuildService> pidCollectorService = gradle.getSharedServices().registerIfAbsent("pidCollector", PidCollectorBuildService.class, spec -> spec.parameters(
            params -> params.getPidFile().set(outFile)
        ));
        gradle.getServices().get(BuildEventsListenerRegistry.class).onTaskCompletion(pidCollectorService);
    }

    public interface Parameters extends BuildServiceParameters {
        RegularFileProperty getPidFile();
    }

    public PidCollectorBuildService() throws Exception {
        GFileUtils.writeFile(getPid().toString(), getParameters().getPidFile().get().getAsFile());
    }

    private Integer getPid() throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        // With Gradle 6.6 we can't inject `ProcessEnvironment`, so that is why we access the runtime MXBean here.
        // On Java 9 we could use the nicer ProcessHandle.
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        java.lang.reflect.Field jvm = runtime.getClass().getDeclaredField("jvm");
        jvm.setAccessible(true);
        sun.management.VMManagement mgmt =
            (sun.management.VMManagement) jvm.get(runtime);
        java.lang.reflect.Method pid_method =
            mgmt.getClass().getDeclaredMethod("getProcessId");
        pid_method.setAccessible(true);

        return (Integer) pid_method.invoke(mgmt);
    }

    @Override
    public void onFinish(FinishEvent event) {
    }
}

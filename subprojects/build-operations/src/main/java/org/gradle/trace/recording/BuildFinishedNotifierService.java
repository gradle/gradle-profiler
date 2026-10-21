package org.gradle.trace.recording;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;
import org.gradle.trace.pid.CurrentProcess;

/**
 * Notifies the profiler when it is closed at the end of the build.
 */
public abstract class BuildFinishedNotifierService implements BuildService<BuildFinishedNotifierService.Parameters>, OperationCompletionListener, AutoCloseable {

    static void register(GradleInternal gradle, int port) {
        Provider<BuildFinishedNotifierService> service = gradle.getSharedServices().registerIfAbsent("buildFinishedNotifier", BuildFinishedNotifierService.class, spec -> {
            spec.getParameters().getPort().set(port);
        });
        // Make sure the service is used, so it is closed at the end of the build
        gradle.getServices().get(BuildEventsListenerRegistry.class).onTaskCompletion(service);
    }

    public interface Parameters extends BuildServiceParameters {
        Property<Integer> getPort();
    }

    @Override
    public void onFinish(FinishEvent event) {
    }

    @Override
    public void close() {
        // Resolve the PID here, as the configuration cache restores the parameters in later build processes
        BuildFinishedNotifier.notifyBuildFinished(getParameters().getPort().get(), String.valueOf(CurrentProcess.getPid()));
    }
}

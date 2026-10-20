package org.gradle.trace.recording;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;

/**
 * Notifies the profiler when it is closed at the end of the build.
 */
public abstract class BuildFinishedNotifierService implements BuildService<BuildFinishedNotifierService.Parameters>, OperationCompletionListener, AutoCloseable {

    static void register(GradleInternal gradle, int port, String pid) {
        Provider<BuildFinishedNotifierService> service = gradle.getSharedServices().registerIfAbsent("buildFinishedNotifier", BuildFinishedNotifierService.class, spec -> {
            spec.getParameters().getPort().set(port);
            spec.getParameters().getPid().set(pid);
        });
        // Make sure the service is used, so it is closed at the end of the build
        gradle.getServices().get(BuildEventsListenerRegistry.class).onTaskCompletion(service);
    }

    public interface Parameters extends BuildServiceParameters {
        Property<Integer> getPort();

        Property<String> getPid();
    }

    @Override
    public void onFinish(FinishEvent event) {
    }

    @Override
    public void close() {
        BuildFinishedNotifier.notifyBuildFinished(getParameters().getPort().get(), getParameters().getPid().get());
    }
}

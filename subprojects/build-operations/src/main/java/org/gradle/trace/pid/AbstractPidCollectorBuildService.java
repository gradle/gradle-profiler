package org.gradle.trace.pid;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public abstract class AbstractPidCollectorBuildService implements BuildService<AbstractPidCollectorBuildService.Parameters>, OperationCompletionListener {

    public static <T extends AbstractPidCollectorBuildService> void registerBuildService(Class<T> buildServiceClass, GradleInternal gradle, File outFile) {
        Provider<T> pidCollectorService = gradle.getSharedServices().registerIfAbsent("pidCollector", buildServiceClass, spec -> spec.parameters(
            params -> params.getPidFile().set(outFile)
        ));
        gradle.getServices().get(BuildEventsListenerRegistry.class).onTaskCompletion(pidCollectorService);
    }

    public interface Parameters extends BuildServiceParameters {
        RegularFileProperty getPidFile();
    }

    protected AbstractPidCollectorBuildService() {
        try {
            Files.write(getParameters().getPidFile().get().getAsFile().toPath(), getPid().toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected abstract Long getPid();

    @Override
    public void onFinish(FinishEvent event) {
    }
}

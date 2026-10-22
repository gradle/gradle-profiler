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

    public PidCollectorBuildService() {
        try {
            Files.write(getParameters().getPidFile().get().getAsFile().toPath(), String.valueOf(CurrentProcess.getPid()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void onFinish(FinishEvent event) {
    }
}

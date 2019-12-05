package org.gradle.trace.buildops;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.execution.RunRootBuildWorkBuildOperationType;
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal;
import org.gradle.internal.operations.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("unused")
public class BuildOperationTrace {

    private final BuildEventListenerRegistryInternal registry;
    private final BuildServiceRegistry sharedServices;

    public BuildOperationTrace(GradleInternal gradle) {
        registry = gradle.getServices().get(BuildEventListenerRegistryInternal.class);
        sharedServices = gradle.getSharedServices();
    }

    public BuildOperationTrace measureConfigurationTime(File outFile) {
        Provider<TimeToFirstTaskRecordingListener> listenerProvider = sharedServices.registerIfAbsent("time-to-first-task", TimeToFirstTaskRecordingListener.class, spec -> {
            spec.getParameters().getOutputFile().set(outFile);
        });
        registry.onOperationCompletion(listenerProvider);
        return this;
    }

    public BuildOperationTrace measureBuildOperation(String buildOperationName, File outFile) {
        Provider<BuildOperationDurationRecordingListener> listenerProvider = sharedServices.registerIfAbsent("build-op-" + buildOperationName, BuildOperationDurationRecordingListener.class, spec -> {
            spec.getParameters().getCapturedBuildOperation().set(buildOperationName);
            spec.getParameters().getOutputFile().set(outFile);
        });
        registry.onOperationCompletion(listenerProvider);
        return this;
    }

    public static abstract class TimeToFirstTaskRecordingListener implements BuildService<TimeToFirstTaskRecordingListener.Params>, BuildOperationListener, AutoCloseable {
        interface Params extends BuildServiceParameters {
            RegularFileProperty getOutputFile();
        }

        private final AtomicLong timeToFirstTask = new AtomicLong(0);

        @Override
        public void started(BuildOperationDescriptor buildOperationDescriptor, OperationStartEvent operationStartEvent) {
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent operationProgressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperationDescriptor, OperationFinishEvent operationFinishEvent) {
            if (buildOperationDescriptor.getDetails() instanceof RunRootBuildWorkBuildOperationType.Details) {
                RunRootBuildWorkBuildOperationType.Details details = (RunRootBuildWorkBuildOperationType.Details) buildOperationDescriptor.getDetails();
                long duration = operationFinishEvent.getStartTime() - details.getBuildStartTime();
                timeToFirstTask.set(duration);
            }
        }

        @Override
        public void close() throws IOException {
            Files.write(getParameters().getOutputFile().get().getAsFile().toPath(), String.valueOf(timeToFirstTask.longValue()).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static abstract class BuildOperationDurationRecordingListener implements BuildService<BuildOperationDurationRecordingListener.Params>, BuildOperationListener, AutoCloseable {
        interface Params extends BuildServiceParameters {
            RegularFileProperty getOutputFile();

            Property<String> getCapturedBuildOperation();
        }

        private final AtomicLong buildOperationTime = new AtomicLong(0);
        private final AtomicLong operationCount = new AtomicLong(0);

        private final Class<?> capturedBuildOperation;

        public BuildOperationDurationRecordingListener() throws ClassNotFoundException {
            capturedBuildOperation = Class.forName(getParameters().getCapturedBuildOperation().get() + "$Details");
        }

        @Override
        public void started(BuildOperationDescriptor buildOperationDescriptor, OperationStartEvent operationStartEvent) {
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent operationProgressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperationDescriptor, OperationFinishEvent operationFinishEvent) {
            Object details = buildOperationDescriptor.getDetails();
            if (buildOperationDescriptor.getDetails() != null && capturedBuildOperation.isAssignableFrom(details.getClass())) {
                operationCount.incrementAndGet();
                buildOperationTime.addAndGet(operationFinishEvent.getEndTime() - operationFinishEvent.getStartTime());
            }
        }

        @Override
        public void close() throws IOException {
            Files.write(getParameters().getOutputFile().get().getAsFile().toPath(), String.valueOf(buildOperationTime.longValue()).getBytes(StandardCharsets.UTF_8));
        }
    }
}

package org.gradle.trace.buildops;

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.trace.stream.AsyncWriter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("unused")
public class BuildOperationTrace {

    private final BuildOperationListenerManager manager;
    private final BuildRequestMetaData requestMetaData;

    public BuildOperationTrace(GradleInternal gradle) {
        manager = gradle.getServices().get(BuildOperationListenerManager.class);
        requestMetaData = gradle.getServices().get(BuildRequestMetaData.class);
    }

    public BuildOperationTrace measureConfigurationTime(File outFile) {
        TimeToFirstTaskRecordingListener buildOperationListener = new TimeToFirstTaskRecordingListener(manager, requestMetaData, outFile.toPath());
        manager.addListener(buildOperationListener);
        return this;
    }

    public BuildOperationTrace measureBuildOperation(String buildOperationName, File outFile) throws ClassNotFoundException {
        BuildOperationDurationRecordingListener buildOperationListener = new BuildOperationDurationRecordingListener(buildOperationName, outFile.toPath(), manager);
        manager.addListener(buildOperationListener);
        return this;
    }

    private static class TimeToFirstTaskRecordingListener extends DetachingBuildOperationListener {

        private final Path outPath;
        private final BuildRequestMetaData buildRequestMetaData;

        private AsyncWriter<Long> writer;

        TimeToFirstTaskRecordingListener(BuildOperationListenerManager manager, BuildRequestMetaData buildRequestMetaData, Path outPath) {
            super(manager);
            this.outPath = outPath;
            this.buildRequestMetaData = buildRequestMetaData;
        }

        private AsyncWriter<Long> ensureWriter() {
            if (writer == null) {
                writer = new AsyncWriter<>(outPath, (l, w) -> w.println(l));
            }
            return writer;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperationDescriptor, OperationStartEvent operationStartEvent) {
            if (buildOperationDescriptor.getOperationType() == BuildOperationCategory.RUN_WORK_ROOT_BUILD) {
                long duration = operationStartEvent.getStartTime() - buildRequestMetaData.getStartTime();
                AsyncWriter<Long> writer = ensureWriter();
                writer.append(duration);
                writer.finished();
            }
        }

        @Override
        public void close() {
            if (writer != null) {
                writer.stop();
            }
        }
    }

    private static class BuildOperationDurationRecordingListener extends DetachingBuildOperationListener {

        private final Class<?> capturedBuildOperation;
        private final Path outPath;
        private final AtomicLong buildOperationTime = new AtomicLong(0);

        private AsyncWriter<Long> writer;

        BuildOperationDurationRecordingListener(String capturedBuildOperation, Path outPath, BuildOperationListenerManager manager) throws ClassNotFoundException {
            super(manager);
            this.capturedBuildOperation = Class.forName(capturedBuildOperation + "$Details");
            this.outPath = outPath;
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperationDescriptor, OperationFinishEvent operationFinishEvent) {
            Object details = buildOperationDescriptor.getDetails();
            if (buildOperationDescriptor.getDetails() != null && capturedBuildOperation.isAssignableFrom(details.getClass())) {
                buildOperationTime.addAndGet(operationFinishEvent.getEndTime() - operationFinishEvent.getStartTime());
            }
            super.finished(buildOperationDescriptor, operationFinishEvent);
        }

        @Override
        public void close() throws Exception {
            Files.write(outPath, String.valueOf(buildOperationTime.longValue()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private abstract static class DetachingBuildOperationListener implements BuildOperationListener, AutoCloseable {
        private final BuildOperationListenerManager manager;

        DetachingBuildOperationListener(BuildOperationListenerManager manager) {
            this.manager = manager;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperationDescriptor, OperationStartEvent operationStartEvent) {
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent operationProgressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperationDescriptor, OperationFinishEvent operationFinishEvent) {
            if (buildOperationDescriptor.getOperationType() == BuildOperationCategory.RUN_WORK_ROOT_BUILD) {
                try {
                    close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    manager.removeListener(this);
                }
            }
        }
    }
}

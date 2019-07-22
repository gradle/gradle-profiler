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

@SuppressWarnings("unused")
public class BuildOperationTrace {

    private final BuildOperationListenerManager manager;
    private final BuildRequestMetaData requestMetaData;

    public BuildOperationTrace(GradleInternal gradle) {
        manager = gradle.getServices().get(BuildOperationListenerManager.class);
        requestMetaData = gradle.getServices().get(BuildRequestMetaData.class);
    }

    public BuildOperationTrace measureConfigurationTime(File outFile) {
        TimeToFirstTaskRecordingListener buildOperationListener = new TimeToFirstTaskRecordingListener(manager, requestMetaData, outFile);
        manager.addListener(buildOperationListener);
        return this;
    }

    public BuildOperationTrace measureBuildOperation(String buildOperationName, File outFile) throws ClassNotFoundException {
        BuildOperationDurationRecordingListener buildOperationListener = new BuildOperationDurationRecordingListener(buildOperationName, outFile, manager);
        manager.addListener(buildOperationListener);
        return this;
    }

    private static class TimeToFirstTaskRecordingListener extends DetachingBuildOperationListener {

        private final BuildRequestMetaData buildRequestMetaData;

        private AsyncWriter<Long> writer;

        TimeToFirstTaskRecordingListener(BuildOperationListenerManager manager, BuildRequestMetaData buildRequestMetaData, File outFile) {
            super(outFile, manager);
            this.buildRequestMetaData = buildRequestMetaData;
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
    }

    private static class BuildOperationDurationRecordingListener extends DetachingBuildOperationListener {

        private final Class<?> capturedBuildOperation;

        private AsyncWriter<Long> writer;

        BuildOperationDurationRecordingListener(String capturedBuildOperation, File outFile, BuildOperationListenerManager manager) throws ClassNotFoundException {
            super(outFile, manager);
            this.capturedBuildOperation = Class.forName(capturedBuildOperation + "$Details");
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperationDescriptor, OperationFinishEvent operationFinishEvent) {
            Object details = buildOperationDescriptor.getDetails();
            if (buildOperationDescriptor.getDetails() != null && capturedBuildOperation.isAssignableFrom(details.getClass())) {
                AsyncWriter<Long> writer = ensureWriter();
                writer.append(operationFinishEvent.getEndTime() - operationFinishEvent.getStartTime());
            }
            super.finished(buildOperationDescriptor, operationFinishEvent);
        }
    }

    private static class DetachingBuildOperationListener implements BuildOperationListener {
        private final File outFile;
        private final BuildOperationListenerManager manager;

        private AsyncWriter<Long> writer;

        DetachingBuildOperationListener(File outFile, BuildOperationListenerManager manager) {
            this.outFile = outFile;
            this.manager = manager;
        }

        protected AsyncWriter<Long> ensureWriter() {
            if (writer == null) {
                writer = new AsyncWriter<>(outFile, (l, w) -> w.println(l));
            }
            return writer;
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
                if (writer != null) {
                    writer.stop();
                }
                manager.removeListener(this);
            }
        }
    }
}

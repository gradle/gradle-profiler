package org.gradle.trace.buildops;

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.operations.*;
import org.gradle.trace.stream.AsyncWriter;

import java.io.File;

@SuppressWarnings("unused")
public class BuildOperationTrace {
    public static void start(GradleInternal gradle, File outFile) {
        BuildOperationListenerManager manager = gradle.getServices().get(BuildOperationListenerManager.class);
        BuildRequestMetaData requestMetaData = gradle.getServices().get(BuildRequestMetaData.class);
        RecordingListener buildOperationListener = new RecordingListener(manager, requestMetaData, outFile);
        manager.addListener(buildOperationListener);
    }

    private static class RecordingListener implements BuildOperationListener {

        private final BuildOperationListenerManager manager;
        private final BuildRequestMetaData buildRequestMetaData;
        private final File outFile;

        private AsyncWriter<Long> writer;

        RecordingListener(BuildOperationListenerManager manager, BuildRequestMetaData buildRequestMetaData, File outFile) {
            this.manager = manager;
            this.buildRequestMetaData = buildRequestMetaData;
            this.outFile = outFile;
        }

        private AsyncWriter<Long> ensureWriter() {
            if (writer == null) {
                writer = new AsyncWriter<>(outFile, (l, w) -> w.print(l));
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

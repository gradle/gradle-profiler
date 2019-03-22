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
        AsyncWriter<Long> writer = new AsyncWriter<>(outFile, (l, w) -> w.print(l));
        RecordingListener buildOperationListener = new RecordingListener(writer, requestMetaData.getStartTime());
        manager.addListener(buildOperationListener);
        gradle.buildFinished((g) -> {
            manager.removeListener(buildOperationListener);
            writer.stop();
        });
    }

    private static class RecordingListener implements BuildOperationListener {
        private final AsyncWriter<Long> writer;
        private final long startTime;

        RecordingListener(AsyncWriter<Long> writer, long startTime) {
            this.writer = writer;
            this.startTime = startTime;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperationDescriptor, OperationStartEvent operationStartEvent) {
            if (buildOperationDescriptor.getOperationType() == BuildOperationCategory.RUN_WORK_ROOT_BUILD) {
                long duration = operationStartEvent.getStartTime() - startTime;
                writer.append(duration);
                writer.finished();
            }
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent operationProgressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperationDescriptor, OperationFinishEvent operationFinishEvent) {
        }
    }
}

package org.gradle.trace.buildops;

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.operations.*;

@SuppressWarnings("unused")
public class BuildOperationTrace {
    public static void start(GradleInternal gradle) {
        BuildOperationListenerManager manager = gradle.getServices().get(BuildOperationListenerManager.class);
        BuildRequestMetaData requestMetaData = gradle.getServices().get(BuildRequestMetaData.class);
        RecordingListener buildOperationListener = new RecordingListener(requestMetaData.getStartTime());
        manager.addListener(buildOperationListener);
        gradle.buildFinished((g) -> manager.removeListener(buildOperationListener));
    }

    private static class RecordingListener implements BuildOperationListener {
        private final long startTime;

        public RecordingListener(long startTime) {
            this.startTime = startTime;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperationDescriptor, OperationStartEvent operationStartEvent) {
            if (buildOperationDescriptor.getOperationType() == BuildOperationCategory.RUN_WORK_ROOT_BUILD) {
                System.out.println("-> started tasks at " + operationStartEvent.getStartTime() + " duration = " + (operationStartEvent.getStartTime() - startTime));
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

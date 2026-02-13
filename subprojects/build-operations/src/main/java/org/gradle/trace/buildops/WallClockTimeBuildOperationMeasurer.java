package org.gradle.trace.buildops;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import org.gradle.internal.operations.OperationFinishEvent;

import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;

final class WallClockTimeBuildOperationMeasurer implements BuildOperationMeasurer {
    // This would be much simpler and based on OperationStartEvent, but we can't rely on those coming in
    // with an order consistent with their start times. So we must record all ranges.
    private final RangeSet<Long> operationTimeRanges = TreeRangeSet.create();
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void update(OperationFinishEvent event) {
        Range<Long> eventRange = Range.closed(event.getStartTime(), event.getEndTime());

        lock.lock();
        try {
            operationTimeRanges.add(eventRange);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Duration computeFinalValue() {
        long totalTime = 0;
        lock.lock();
        try {
            for (Range<Long> range : operationTimeRanges.asRanges()) {
                totalTime += range.upperEndpoint() - range.lowerEndpoint();
            }
        } finally {
            lock.unlock();
        }
        return Duration.ofMillis(totalTime);
    }
}

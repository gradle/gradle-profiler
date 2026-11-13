package org.gradle.profiler.perfetto.jfr.processor;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;
import org.gradle.profiler.perfetto.jfr.ConverterSession;
import org.gradle.profiler.perfetto.jfr.JfrRecordFields;
import org.gradle.profiler.perfetto.jfr.PerfettoTrackEventBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Converts Gradle build operation intervals into Perfetto slices laid out across lanes.
 *
 * <p>
 * A lane is a synthetic layout track (surfaced in the trace as {@code Virtual NN}), not a JVM
 * thread. Operations that overlap in time are placed on different lanes, so the number of occupied
 * lanes at any instant reflects the degree of parallelism; non-overlapping operations reuse a lane
 * to keep the trace compact.
 * <p>
 * A child operation stays on its parent's lane when the parent is the lane's current top, so nesting
 * renders as depth within a lane rather than as extra lanes.
 *
 * <p>This relies on Gradle build operations being properly nested: a child always finishes no later
 * than its parent. That containment makes every lane a laminar stack whose top is always the next to
 * finish, which is what lets a lane be drained by a single end-time check.
 *
 * <p>Consumes:
 * <ul>
 *   <li>{@code org.gradle.internal.operations.BuildOperation}</li>
 * </ul>
 */
public final class GradleBuildOperationProcessor extends AbstractJfrEventProcessor {
    private static final Comparator<BuildOperationRecord> BUILD_OPERATION_ORDER = Comparator
        .comparingLong(BuildOperationRecord::startNs)
        .thenComparing(Comparator.comparingLong(BuildOperationRecord::endNs).reversed())
        .thenComparingInt(BuildOperationRecord::sequence);

    private final List<BuildOperationRecord> bufferedEvents = new ArrayList<>();

    public GradleBuildOperationProcessor() {
        super("org.gradle.internal.operations.BuildOperation");
    }

    @Override
    protected void processMatchingEvent(RecordedEvent event, ConverterSession context) throws IOException {
        long startNs = context.toEpochNanos(event.getStartTime());
        long endNs = Math.max(startNs, context.toEpochNanos(event.getEndTime()));
        JfrRecordFields fields = JfrRecordFields.of(event);

        bufferedEvents.add(new BuildOperationRecord(
            startNs,
            endNs,
            bufferedEvents.size(),
            fields.requiredPositiveLong("operationId"),
            Math.max(0L, fields.longOrZero("parentId")),
            resolveDisplayName(fields),
            failureDetails(fields)
        ));
    }

    // Visible for tests: JFR events cannot be synthesized with explicit timestamps, so tests inject
    // records directly to exercise the layout and emission performed by finish().
    void buffer(BuildOperationRecord record) {
        bufferedEvents.add(record);
    }

    @Override
    public Optional<Void> finish(@Nullable ConverterSession context) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("ConverterSession is required to emit build operations");
        }
        if (bufferedEvents.isEmpty()) {
            return Optional.empty();
        }

        try {
            for (LaneAssignment assignment : assignLanes(bufferedEvents)) {
                BuildOperationRecord record = assignment.record();
                long trackId = context.trackRegistry().ensureBuildOperationTrack(assignment.laneId());
                record.emit(context, trackId);
            }
        } finally {
            bufferedEvents.clear();
        }
        return Optional.empty();
    }

    // Pure layout policy, kept separate from emission so it can be unit-tested without JFR or Perfetto.
    // Returns each operation paired with its lane, in processing order.
    static List<LaneAssignment> assignLanes(List<BuildOperationRecord> records) {
        // Copy before sorting so we don't reorder the caller's list. The sort guarantees a parent is
        // placed before its children (earlier start; ties broken so the enclosing operation goes first).
        List<BuildOperationRecord> sorted = new ArrayList<>(records);
        sorted.sort(BUILD_OPERATION_ORDER);

        List<Lane> lanes = new ArrayList<>();
        List<LaneAssignment> assignments = new ArrayList<>(sorted.size());
        for (BuildOperationRecord record : sorted) {
            Lane lane = assignLane(lanes, record);
            lane.push(record.operationId(), record.endNs());
            assignments.add(new LaneAssignment(record, lane.id()));
        }
        return assignments;
    }

    private static Lane assignLane(List<Lane> lanes, BuildOperationRecord record) {
        Lane parentLane = null;
        Lane firstEmpty = null;
        for (Lane lane : lanes) {
            // Retire operations that have finished by the time this one starts, freeing the lane.
            lane.releaseEndedBy(record.startNs());
            // An empty lane's top reads as 0, so a root operation (parentId 0) matches it here too.
            if (parentLane == null && lane.topOperationId() == record.parentId()) {
                parentLane = lane;
            }
            if (firstEmpty == null && lane.isEmpty()) {
                firstEmpty = lane;
            }
        }
        // Keep a child on its parent's lane, so nesting renders as depth rather than an extra lane.
        if (parentLane != null) {
            return parentLane;
        }
        // No parent to follow: reclaim the lowest drained lane to keep the layout compact.
        if (firstEmpty != null) {
            return firstEmpty;
        }
        // Nothing free: spill onto a new lane. Overlapping operations land here, which is what makes
        // parallelism show up as added height.
        Lane lane = new Lane(lanes.size() + 1);
        lanes.add(lane);
        return lane;
    }

    private static String resolveDisplayName(JfrRecordFields fields) {
        String displayName = fields.nullableString("displayName");
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        long operationId = fields.longOrZero("operationId");
        if (operationId > 0) {
            return "Build Operation " + operationId;
        }
        return "Build Operation";
    }

    // The event carries no explicit failure flag: an operation failed if either failure field is set.
    @Nullable
    private static FailureDetails failureDetails(JfrRecordFields fields) {
        String failureType = fields.nullableString("failureType");
        String failureMessage = fields.nullableString("failureMessage");
        return failureType == null && failureMessage == null
            ? null
            : new FailureDetails(failureType, failureMessage);
    }

    record BuildOperationRecord(
        long startNs,
        long endNs,
        // Original ingestion index in JFR file order, used as the final stable tie-breaker.
        int sequence,
        // Operation ID from the build operation. Must always be positive.
        long operationId,
        // Parent operation ID from the build operation. Zero for a root operation.
        long parentId,
        // Display name from the build operation.
        String displayName,
        // Non-null only for failed operations; inner fields may still be absent.
        @Nullable
        FailureDetails failure
    ) {
        private void emit(ConverterSession context, long trackId) throws IOException {
            PerfettoTrackEventBuilder slice = context.trackEvent(startNs, endNs)
                .onTrack(trackId)
                .name(displayName)
                .annotate("operationId", operationId)
                .annotate("parentId", parentId);
            if (failure != null) {
                slice.annotateIfPresent("failureType", failure.type())
                    .annotateIfPresent("failureMessage", failure.message());
            }
            slice.emitSlice();
        }
    }

    record FailureDetails(
        @Nullable
        String type,
        @Nullable
        String message
    ) {
    }

    record LaneAssignment(
        BuildOperationRecord record,
        int laneId
    ) {
    }

    // A single layout lane: a stack of the operations currently open on it. Children finish no later
    // than their parents, so the top is always the next to finish and the lane drains from the top.
    private static final class Lane {
        private final int id;
        private final ArrayDeque<Active> stack = new ArrayDeque<>();

        private Lane(int id) {
            this.id = id;
        }

        private int id() {
            return id;
        }

        private boolean isEmpty() {
            return stack.isEmpty();
        }

        private long topOperationId() {
            // 0 when empty.
            Active top = stack.peek();
            return top == null ? 0L : top.operationId();
        }

        private void push(long operationId, long endNs) {
            stack.push(new Active(operationId, endNs));
        }

        private void releaseEndedBy(long timestampNs) {
            while (!stack.isEmpty() && stack.peek().endNs() <= timestampNs) {
                stack.pop();
            }
        }
    }

    private record Active(long operationId, long endNs) {
    }
}

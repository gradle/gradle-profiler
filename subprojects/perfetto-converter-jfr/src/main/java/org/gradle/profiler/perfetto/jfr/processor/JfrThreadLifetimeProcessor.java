package org.gradle.profiler.perfetto.jfr.processor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import org.gradle.profiler.perfetto.jfr.ConverterSession;
import org.gradle.profiler.perfetto.jfr.ThreadIdentity;
import org.jspecify.annotations.Nullable;

/**
 * Collects thread lifetime information and emits Alive slices when the phase finishes.
 */
public final class JfrThreadLifetimeProcessor implements JfrEventProcessor<Void> {
    private static final String BUILD_OPERATION_EVENT = "org.gradle.internal.operations.BuildOperation";
    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";
    private static final String NATIVE_METHOD_SAMPLE_EVENT = "jdk.NativeMethodSample";
    private static final String THREAD_START_EVENT = "jdk.ThreadStart";
    private static final String THREAD_END_EVENT = "jdk.ThreadEnd";
    private static final Map<String, String> THREAD_STATE_EVENTS = Map.of(
        "jdk.ThreadPark", "Parked",
        "jdk.JavaMonitorWait", "Monitor wait",
        "jdk.JavaMonitorEnter", "Blocked on monitor",
        "jdk.ThreadSleep", "Sleeping",
        "jdk.SocketRead", "Socket read",
        "jdk.SocketWrite", "Socket write"
    );

    private final Map<Long, ThreadFacts> factsByThread = new LinkedHashMap<>();

    @Override
    public void process(RecordedEvent event, @Nullable ConverterSession context) throws IOException {
        String eventName = event.getEventType().getName();
        if (BUILD_OPERATION_EVENT.equals(eventName)) {
            observeActivity(ThreadIdentity.from(event.getThread()), toEpochNanos(event, context, true), toEpochNanos(event, context, false));
            return;
        }
        if (EXECUTION_SAMPLE_EVENT.equals(eventName) || NATIVE_METHOD_SAMPLE_EVENT.equals(eventName)) {
            long timestampNs = toEpochNanos(event, context, true);
            observeActivity(resolveSampleThread(event), timestampNs, timestampNs);
            return;
        }
        if (THREAD_STATE_EVENTS.containsKey(eventName)) {
            observeActivity(ThreadIdentity.from(event.getThread()), toEpochNanos(event, context, true), toEpochNanos(event, context, false));
            return;
        }
        if (THREAD_START_EVENT.equals(eventName)) {
            observeThreadStart(ThreadIdentity.from(resolveLifecycleThread(event)), toEpochNanos(event, context, true));
            return;
        }
        if (THREAD_END_EVENT.equals(eventName)) {
            observeThreadEnd(ThreadIdentity.from(resolveLifecycleThread(event)), toEpochNanos(event, context, true));
        }
    }

    @Override
    public Optional<Void> finish(@Nullable ConverterSession context) throws IOException {
        if (context == null || factsByThread.isEmpty()) {
            return Optional.empty();
        }
        for (ThreadFacts facts : factsByThread.values()) {
            AliveSlice aliveSlice = facts.toAliveSlice();
            if (aliveSlice == null) {
                continue;
            }
            Long trackId = context.trackRegistry().ensureThreadTrack(aliveSlice.thread());
            if (trackId == null) {
                continue;
            }
            context.trackEvent(aliveSlice.startNs(), aliveSlice.endNs())
                .onTrack(trackId)
                .name("Alive")
                .emitSlice();
        }
        return Optional.empty();
    }

    private void observeActivity(ThreadIdentity thread, long startNs, long endNs) {
        if (thread == null) {
            return;
        }
        ThreadFacts facts = factsByThread.computeIfAbsent(thread.trackKey(), ignored -> new ThreadFacts(thread));
        facts.updateThread(thread);
        facts.observeActivity(startNs, endNs);
    }

    private void observeThreadStart(ThreadIdentity thread, long timestampNs) {
        if (thread == null) {
            return;
        }
        ThreadFacts facts = factsByThread.computeIfAbsent(thread.trackKey(), ignored -> new ThreadFacts(thread));
        facts.updateThread(thread);
        facts.observeThreadStart(timestampNs);
    }

    private void observeThreadEnd(ThreadIdentity thread, long timestampNs) {
        if (thread == null) {
            return;
        }
        ThreadFacts facts = factsByThread.computeIfAbsent(thread.trackKey(), ignored -> new ThreadFacts(thread));
        facts.updateThread(thread);
        facts.observeThreadEnd(timestampNs);
    }

    private static ThreadIdentity resolveSampleThread(RecordedEvent event) {
        if (event.hasField("sampledThread")) {
            try {
                RecordedThread sampledThread = event.getThread("sampledThread");
                ThreadIdentity sampled = ThreadIdentity.from(sampledThread);
                if (sampled != null) {
                    return sampled;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to event thread
            }
        }
        return ThreadIdentity.from(event.getThread());
    }

    private static RecordedThread resolveLifecycleThread(RecordedEvent event) {
        for (String fieldName : new String[]{"thread", "startedThread", "endedThread"}) {
            if (!event.hasField(fieldName)) {
                continue;
            }
            try {
                RecordedThread thread = event.getThread(fieldName);
                if (thread != null) {
                    return thread;
                }
            } catch (IllegalArgumentException ignored) {
                // continue
            }
        }
        return event.getThread();
    }

    private static long toEpochNanos(RecordedEvent event, @Nullable ConverterSession context, boolean startTime) {
        if (context != null) {
            return context.toEpochNanos(startTime ? event.getStartTime() : event.getEndTime());
        }
        return (startTime ? event.getStartTime() : event.getEndTime()).getEpochSecond() * 1_000_000_000L
            + (startTime ? event.getStartTime() : event.getEndTime()).getNano();
    }

    private static final class ThreadFacts {
        private ThreadIdentity thread;
        private Long explicitStartNs;
        private Long explicitEndNs;
        private Long firstObservedStartNs;
        private Long lastObservedEndNs;

        private ThreadFacts(ThreadIdentity thread) {
            this.thread = thread;
        }

        private void updateThread(ThreadIdentity candidate) {
            if (candidate != null) {
                thread = candidate;
            }
        }

        private void observeActivity(long startNs, long endNs) {
            long normalizedStart = Math.min(startNs, endNs);
            long normalizedEnd = Math.max(startNs, endNs);
            firstObservedStartNs = firstObservedStartNs == null ? normalizedStart : Math.min(firstObservedStartNs, normalizedStart);
            lastObservedEndNs = lastObservedEndNs == null ? normalizedEnd : Math.max(lastObservedEndNs, normalizedEnd);
        }

        private void observeThreadStart(long timestampNs) {
            explicitStartNs = explicitStartNs == null ? timestampNs : Math.min(explicitStartNs, timestampNs);
        }

        private void observeThreadEnd(long timestampNs) {
            explicitEndNs = explicitEndNs == null ? timestampNs : Math.max(explicitEndNs, timestampNs);
        }

        private AliveSlice toAliveSlice() {
            if (thread == null || explicitStartNs == null || explicitEndNs == null || explicitEndNs < explicitStartNs) {
                return null;
            }
            long startNs = firstObservedStartNs == null ? explicitStartNs : Math.min(explicitStartNs, firstObservedStartNs);
            long endNs = lastObservedEndNs == null ? explicitEndNs : Math.max(explicitEndNs, lastObservedEndNs);
            return new AliveSlice(thread, startNs, endNs);
        }
    }

    private record AliveSlice(
        ThreadIdentity thread,
        long startNs,
        long endNs
    ) {
    }
}

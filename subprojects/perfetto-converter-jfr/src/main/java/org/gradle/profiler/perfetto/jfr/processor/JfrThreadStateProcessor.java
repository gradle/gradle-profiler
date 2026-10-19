package org.gradle.profiler.perfetto.jfr.processor;

import java.io.IOException;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import org.gradle.profiler.perfetto.jfr.ConverterSession;
import org.gradle.profiler.perfetto.jfr.ThreadIdentity;

/**
 * Emits blocking and waiting thread-state intervals onto per-thread Perfetto tracks.
 *
 * <p>Consumes:
 * <ul>
 *   <li>{@code jdk.ThreadPark}</li>
 *   <li>{@code jdk.JavaMonitorWait}</li>
 *   <li>{@code jdk.JavaMonitorEnter}</li>
 *   <li>{@code jdk.ThreadSleep}</li>
 *   <li>{@code jdk.SocketRead}</li>
 *   <li>{@code jdk.SocketWrite}</li>
 * </ul>
 */
public final class JfrThreadStateProcessor implements JfrEventProcessor<Void> {
    private static final Map<String, String> EVENT_LABELS = Map.of(
        "jdk.ThreadPark", "Parked",
        "jdk.JavaMonitorWait", "Monitor wait",
        "jdk.JavaMonitorEnter", "Blocked on monitor",
        "jdk.ThreadSleep", "Sleeping",
        "jdk.SocketRead", "Socket read",
        "jdk.SocketWrite", "Socket write"
    );

    @Override
    public void process(RecordedEvent event, ConverterSession context) throws IOException {
        String label = EVENT_LABELS.get(event.getEventType().getName());
        if (label == null) {
            return;
        }

        ThreadIdentity thread = ThreadIdentity.from(event.getThread());
        Long trackId = context.trackRegistry().ensureThreadTrack(thread);
        if (trackId == null) {
            return;
        }

        context.trackEvent(event)
            .onTrack(trackId)
            .name(label)
            .emitSlice();
    }
}

package org.gradle.profiler.client.protocol.messages;

import java.util.concurrent.atomic.AtomicInteger;

public class StudioRequest implements Message {

    public enum StudioRequestType {
        SYNC,
        CLEANUP_CACHE,
        EXIT_IDE,
        STOP_RECEIVING_EVENTS
    }

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    private final int id;
    private final StudioRequestType type;

    public StudioRequest(StudioRequestType type) {
        this(ID_GENERATOR.incrementAndGet(), type);
    }

    public StudioRequest(int requestId, StudioRequestType type) {
        this.id = requestId;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public StudioRequestType getType() {
        return type;
    }
}

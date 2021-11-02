package org.gradle.profiler.client.protocol.messages;

import org.gradle.profiler.client.protocol.Message;

import java.util.concurrent.atomic.AtomicInteger;

public class StudioRequest extends Message {

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    public enum StudioRequestType {
        SYNC,
        EXIT
    }

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

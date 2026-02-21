package org.gradle.profiler.client.protocol.messages;

import java.util.concurrent.atomic.AtomicInteger;

public class IdeRequest implements Message {

    public enum IdeRequestType {
        SYNC,
        CLEANUP_CACHE,
        EXIT_IDE,
        STOP_RECEIVING_EVENTS
    }

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    private final int id;
    private final IdeRequestType type;

    public IdeRequest(IdeRequestType type) {
        this(ID_GENERATOR.incrementAndGet(), type);
    }

    public IdeRequest(int requestId, IdeRequestType type) {
        this.id = requestId;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public IdeRequestType getType() {
        return type;
    }
}

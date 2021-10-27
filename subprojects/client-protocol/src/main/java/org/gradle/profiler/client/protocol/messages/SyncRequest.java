package org.gradle.profiler.client.protocol.messages;

import org.gradle.profiler.client.protocol.Message;

import java.util.concurrent.atomic.AtomicInteger;

public class SyncRequest extends Message {

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    private final int id;

    public SyncRequest() {
        this(ID_GENERATOR.get());
    }

    public SyncRequest(int syncId) {
        this.id = syncId;
    }

    public int getId() {
        return id;
    }
}

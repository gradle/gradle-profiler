package org.gradle.profiler.client.protocol.messages;

public class SyncStarted extends Message {
    private final int id;

    public SyncStarted(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "sync started " + id;
    }

    public int getId() {
        return id;
    }
}

package org.gradle.profiler.client.protocol.messages;

public class StudioCacheCleanupCompleted implements Message {

    private final int id;

    public StudioCacheCleanupCompleted(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

}

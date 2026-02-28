package org.gradle.profiler.client.protocol.messages;

public class IdeCacheCleanupCompleted implements Message {

    private final int id;

    public IdeCacheCleanupCompleted(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

}

package org.gradle.profiler.client.protocol.messages;

public class GradleInvocationStarted implements Message {
    private final int id;

    public GradleInvocationStarted(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "gradle invocation started " + id;
    }

    public int getId() {
        return id;
    }
}

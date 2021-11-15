package org.gradle.profiler.client.protocol.agent;

import org.gradle.profiler.client.protocol.Client;

public enum AgentClientContainer {
    INSTANCE;

    private volatile Client client;

    public Client getClient() {
        return client;
    }

    public synchronized void init(Client client) {
        if (this.client != null) {
            throw new IllegalStateException("Client already initialized");
        }
        this.client = client;
    }
}

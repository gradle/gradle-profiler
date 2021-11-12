package org.gradle.profiler.client.protocol.agent;

import org.gradle.profiler.client.protocol.Client;

import java.util.concurrent.atomic.AtomicReference;

public enum AgentClientContainer {
    INSTANCE;

    private final AtomicReference<Client> client = new AtomicReference<>();

    public Client getClient() {
        return client.get();
    }

    public synchronized void init(Client client) {
        if (this.client.get() != null) {
            throw new IllegalStateException("Client already initialized");
        }
        this.client.set(client);
    }

}

package org.gradle.profiler.client.protocol;

import org.gradle.profiler.client.protocol.messages.*;
import org.gradle.profiler.client.protocol.serialization.MessageSerializer;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;

public class ServerConnection implements Closeable {
    private final Connection connection;
    private final MessageSerializer serializer;

    public ServerConnection(String peerName, Connection connection) {
        this.connection = connection;
        this.serializer = new MessageSerializer(peerName, connection);
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }

    public void send(GradleInvocationParameters syncParameters) {
        serializer.send(syncParameters);
    }

    public void send(StudioRequest syncRequest) {
        serializer.send(syncRequest);
    }

    public void send(StudioAgentConnectionParameters connectionParameters) {
        serializer.send(connectionParameters);
    }

    public GradleInvocationStarted receiveSyncStarted(Duration timeout) {
        return serializer.receive(GradleInvocationStarted.class, timeout);
    }

    public GradleInvocationCompleted receiveSyncCompleted(Duration timeout) {
        return serializer.receive(GradleInvocationCompleted.class, timeout);
    }

    public StudioSyncRequestCompleted receiveSyncRequestCompleted(Duration timeout) {
        return serializer.receive(StudioSyncRequestCompleted.class, timeout);
    }
}

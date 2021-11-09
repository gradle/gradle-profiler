package org.gradle.profiler.client.protocol;

import org.gradle.profiler.client.protocol.messages.*;
import org.gradle.profiler.client.protocol.serialization.MessageProtocolHandler;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;

public class ServerConnection implements Closeable {
    private final Connection connection;
    private final MessageProtocolHandler serializer;

    public ServerConnection(String peerName, Connection connection) {
        this.connection = connection;
        this.serializer = new MessageProtocolHandler(peerName, connection);
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }

    public void send(Message message) {
        serializer.send(message);
    }

    public GradleInvocationStarted receiveSyncStarted(Duration timeout) {
        return serializer.receive(GradleInvocationStarted.class, timeout);
    }

    public GradleInvocationCompleted receiveGradleInvocationCompleted(Duration timeout) {
        return serializer.receive(GradleInvocationCompleted.class, timeout);
    }

    public StudioSyncRequestCompleted receiveSyncRequestCompleted(Duration timeout) {
        return serializer.receive(StudioSyncRequestCompleted.class, timeout);
    }
}

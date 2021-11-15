package org.gradle.profiler.client.protocol;

import org.gradle.profiler.client.protocol.messages.*;
import org.gradle.profiler.client.protocol.serialization.MessageProtocolHandler;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;

public class ServerConnection implements Closeable {
    private final Connection connection;
    private final MessageProtocolHandler protocolHandler;

    public ServerConnection(String peerName, Connection connection) {
        this.connection = connection;
        this.protocolHandler = new MessageProtocolHandler(peerName, connection);
    }

    @Override
    public void close() throws IOException {
        try(MessageProtocolHandler protocolHandler = this.protocolHandler) {
            connection.close();
        }
    }

    public void send(Message message) {
        protocolHandler.send(message);
    }

    public GradleInvocationStarted receiveSyncStarted(Duration timeout) {
        return protocolHandler.receive(GradleInvocationStarted.class, timeout);
    }

    public GradleInvocationCompleted receiveGradleInvocationCompleted(Duration timeout) {
        return protocolHandler.receive(GradleInvocationCompleted.class, timeout);
    }

    public StudioSyncRequestCompleted receiveSyncRequestCompleted(Duration timeout) {
        return protocolHandler.receive(StudioSyncRequestCompleted.class, timeout);
    }

    public StudioCacheCleanupCompleted receiveCacheCleanupCompleted(Duration timeout) {
        return protocolHandler.receive(StudioCacheCleanupCompleted.class, timeout);
    }
}

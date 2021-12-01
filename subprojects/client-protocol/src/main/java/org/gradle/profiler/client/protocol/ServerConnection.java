package org.gradle.profiler.client.protocol;

import org.gradle.profiler.client.protocol.messages.GradleInvocationCompleted;
import org.gradle.profiler.client.protocol.messages.GradleInvocationStarted;
import org.gradle.profiler.client.protocol.messages.Message;
import org.gradle.profiler.client.protocol.messages.StudioCacheCleanupCompleted;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.client.protocol.serialization.MessageProtocolHandler;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

public class ServerConnection implements Closeable {

    private final Connection connection;
    private final MessageProtocolHandler protocolHandler;

    public ServerConnection(String peerName, Connection connection) {
        this.connection = connection;
        this.protocolHandler = new MessageProtocolHandler(peerName, connection);
    }

    public void send(Message message) {
        protocolHandler.send(message);
    }

    public Optional<GradleInvocationStarted> maybeReceiveGradleInvocationStarted(Duration timeout) {
        return protocolHandler.maybeReceive(GradleInvocationStarted.class, timeout);
    }

    public GradleInvocationStarted receiveGradleInvocationStarted(Duration timeout) {
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

    @Override
    public void close() throws IOException {
        connection.close();
    }
}

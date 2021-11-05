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

    public void send(SyncParameters syncParameters) {
        serializer.send(syncParameters);
    }

    public void send(StudioRequest syncRequest) {
        serializer.send(syncRequest);
    }

    public void send(ConnectionParameters connectionParameters) {
        serializer.send(connectionParameters);
    }

    public SyncStarted receiveSyncStarted(Duration timeout) {
        return serializer.receive(SyncStarted.class, timeout);
    }

    public SyncCompleted receiveSyncCompleted(Duration timeout) {
        return serializer.receive(SyncCompleted.class, timeout);
    }

    public StudioSyncRequestCompleted receiveSyncRequestCompleted(Duration timeout) {
        return serializer.receive(StudioSyncRequestCompleted.class, timeout);
    }
}

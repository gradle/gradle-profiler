package org.gradle.profiler.client.protocol;

import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.SyncRequestCompleted;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;

public class ServerConnection implements Closeable {
    private final Connection connection;
    private final Serializer serializer;

    public ServerConnection(String peerName, Connection connection) {
        this.connection = connection;
        this.serializer = new Serializer(peerName, connection);
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

    public SyncCompleted receiveSyncCompeted(Duration timeout) {
        return serializer.receive(SyncCompleted.class, timeout);
    }

    public SyncRequestCompleted receiveSyncRequestCompeted(Duration timeout) {
        return serializer.receive(SyncRequestCompleted.class, timeout);
    }
}

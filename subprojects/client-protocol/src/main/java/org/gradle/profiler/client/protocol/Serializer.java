package org.gradle.profiler.client.protocol;

import java.io.EOFException;
import java.io.IOException;

public class Serializer {
    private final String peerName;
    private final Connection connection;

    public Serializer(String peerName, Connection connection) {
        this.peerName = peerName;
        this.connection = connection;
    }

    public void send(SyncStarted message) {
        try {
            connection.writeByte((byte) 1);
            connection.writeInt(message.getId());
            connection.flush();
        } catch (IOException e) {
            throw couldNotWrite(e);
        }
    }

    public void send(SyncCompleted message) {
        try {
            connection.writeByte((byte) 2);
            connection.writeInt(message.getId());
            connection.writeLong(message.getDurationMillis());
            connection.flush();
        } catch (IOException e) {
            throw couldNotWrite(e);
        }
    }

    private RuntimeException couldNotWrite(IOException e) {
        return new RuntimeException(String.format("Could not write to %s.", peerName), e);
    }

    public Message receive() {
        try {
            byte tag;
            try {
                tag = connection.readByte();
            } catch (EOFException e) {
                // Disconnected
                return null;
            }
            int id;
            switch (tag) {
                case 1:
                    id = connection.readInt();
                    return new SyncStarted(id);
                case 2:
                    id = connection.readInt();
                    long durationMillis = connection.readLong();
                    return new SyncCompleted(id, durationMillis);
                default:
                    throw new IllegalArgumentException("Received unexpected message on connection.");
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not read from %s.", peerName), e);
        }
    }
}

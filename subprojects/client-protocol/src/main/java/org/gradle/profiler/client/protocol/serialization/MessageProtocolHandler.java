package org.gradle.profiler.client.protocol.serialization;

import org.gradle.profiler.client.protocol.Connection;
import org.gradle.profiler.client.protocol.messages.Message;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Optional;

public class MessageProtocolHandler {

    /**
     * Zero means no timeout for Sockets
     */
    private static final int BODY_READ_TIMEOUT_MS = 0;

    private final String peerName;
    private final Connection connection;

    public MessageProtocolHandler(String peerName, Connection connection) {
        this.peerName = peerName;
        this.connection = connection;
    }

    public void send(Message message) {
        try {
            MessageSerializer serializer = MessageSerializer.getMessageSerializer(message.getClass());
            serializer.writeTo(connection, message);
            connection.flush();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not write to %s.", peerName), e);
        }
    }

    /**
     * Waits for the message for duration of timeout or returns Optional.empty() if waiting timeouts.
     */
    public <T extends Message> Optional<T> maybeReceive(Class<T> type, Duration timeout) {
        return this.receive(timeout);
    }

    /**
     * Waits for the message for duration of timeout and throws exception if waiting timeouts.
     */
    public <T extends Message> T receive(Class<T> type, Duration timeout) {
        return this.<T>receive(timeout)
            .orElseThrow(() -> new IllegalStateException(String.format("Timeout waiting to receive %s.", type)));
    }

    @SuppressWarnings("unchecked")
    private <T extends Message> Optional<T> receive(Duration timeout) {
        try {
            // Since we do a flush after write, there is practically no delay between first byte (tag)
            // and other bytes (body). So it's just important we set a timeout for the tag.
            byte tag = connection.readByte((int) timeout.toMillis());
            MessageSerializer serializer = MessageSerializer.getMessageSerializer(tag);
            return (Optional<T>) Optional.of(serializer.readFrom(connection, BODY_READ_TIMEOUT_MS));
        } catch (EOFException e) {
            throw new IllegalStateException(String.format("Connection to %s has closed.", peerName));
        } catch (SocketTimeoutException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not read from %s.", peerName), e);
        }
    }
}

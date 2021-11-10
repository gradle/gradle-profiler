package org.gradle.profiler.client.protocol.serialization;

import org.gradle.profiler.client.protocol.Connection;
import org.gradle.profiler.client.protocol.messages.Message;

import java.io.EOFException;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MessageProtocolHandler {
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

    public <T extends Message> T receive(Class<T> type, Duration timeout) {
        try {
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(this::receive);
            Object result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return type.cast(result);
        } catch (TimeoutException e) {
            throw new IllegalStateException(String.format("Timeout waiting to receive message from %s.", peerName));
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Object receive() {
        try {
            byte tag = connection.readByte();
            MessageSerializer serializer = MessageSerializer.getMessageSerializer(tag);
            return serializer.readFrom(connection);
        } catch (EOFException e) {
            throw new IllegalStateException(String.format("Connection to %s has closed.", peerName));
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not read from %s.", peerName), e);
        }
    }
}

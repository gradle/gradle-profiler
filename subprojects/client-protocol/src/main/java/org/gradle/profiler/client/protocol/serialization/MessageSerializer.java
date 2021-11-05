package org.gradle.profiler.client.protocol.serialization;

import org.gradle.profiler.client.protocol.Connection;
import org.gradle.profiler.client.protocol.messages.*;

import java.io.EOFException;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MessageSerializer {
    private static final Object NULL = new Object();
    private final String peerName;
    private final Connection connection;

    public MessageSerializer(String peerName, Connection connection) {
        this.peerName = peerName;
        this.connection = connection;
    }

    public void send(Message message) {
        try {
            byte messageId = MessageRegistry.findMessageId(message.getClass());
            connection.writeByte(messageId);
            MessageReaderWriter readerWriter = MessageRegistry.findMessageReaderWriter(message.getClass());
            readerWriter.writeTo(connection, message);
            connection.flush();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not write to %s.", peerName), e);
        }
    }

    public <T extends Message> T receive(Class<T> type, Duration timeout) {
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> {
            try {
                Object result;
                try {
                    result = receive();
                } catch (RuntimeException e) {
                    result = e;
                }
                queue.put(result);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        reader.start();
        Object result;
        try {
            result = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (result == null) {
                reader.interrupt();
                throw new IllegalStateException(String.format("Timeout waiting to receive message from %s.", peerName));
            }
            reader.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (result instanceof RuntimeException) {
            throw (RuntimeException) result;
        }
        if (result == NULL) {
            throw new IllegalStateException(String.format("Connection to %s has closed.", peerName));
        }
        return type.cast(result);
    }

    private Object receive() {
        try {
            byte tag = connection.readByte();
            MessageReaderWriter readerWriter = MessageRegistry.findMessageReaderWriter(tag);
            return readerWriter.readFrom(connection);
        } catch (EOFException e) {
            return NULL;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not read from %s.", peerName), e);
        }
    }
}

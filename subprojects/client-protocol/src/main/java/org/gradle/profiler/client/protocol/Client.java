package org.gradle.profiler.client.protocol;

import org.gradle.profiler.client.protocol.messages.GradleInvocationParameters;
import org.gradle.profiler.client.protocol.messages.Message;
import org.gradle.profiler.client.protocol.messages.StudioAgentConnectionParameters;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.serialization.MessageProtocolHandler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * A singleton that runs inside a client process to communicate with the controller process.
 */
public enum Client {
    INSTANCE;

    private final Object lock = new Object();
    private Connection connection;
    private MessageProtocolHandler serializer;

    public void connect(int port) {
        synchronized (lock) {
            if (connection != null) {
                throw new IllegalStateException("This client is already connected.");
            }
            try {
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                connection = new Connection(socket);
                serializer = new MessageProtocolHandler("controller process", connection);
            } catch (IOException e) {
                throw new RuntimeException("Could not connect to controller process.", e);
            }
        }
    }

    public void send(Message message) {
        synchronized (lock) {
            serializer.send(message);
        }
    }

    public GradleInvocationParameters receiveSyncParameters(Duration timeout) {
        synchronized (lock) {
            return serializer.receive(GradleInvocationParameters.class, timeout);
        }
    }

    public StudioAgentConnectionParameters receiveConnectionParameters(Duration timeout) {
        synchronized (lock) {
            return serializer.receive(StudioAgentConnectionParameters.class, timeout);
        }
    }

    public StudioRequest receiveStudioRequest(Duration timeout) {
        synchronized (lock) {
            return serializer.receive(StudioRequest.class, timeout);
        }
    }

    public void disconnect() {
        synchronized (lock) {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                connection = null;
            }
        }
    }
}

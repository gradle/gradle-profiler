package org.gradle.profiler.client.protocol;

import org.gradle.profiler.client.protocol.messages.GradleInvocationParameters;
import org.gradle.profiler.client.protocol.messages.Message;
import org.gradle.profiler.client.protocol.messages.StudioAgentConnectionParameters;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.serialization.MessageProtocolHandler;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * A client process to communicate with the controller process.
 */
public class Client implements Closeable {

    private final Object lock = new Object();
    private final Connection connection;
    private final MessageProtocolHandler protocolHandler;

    public Client(int port) {
        try {
            Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
            connection = new Connection(socket);
            protocolHandler = new MessageProtocolHandler("controller process", connection);
        } catch (IOException e) {
            throw new RuntimeException("Could not connect to controller process.", e);
        }
    }

    public void send(Message message) {
        synchronized (lock) {
            protocolHandler.send(message);
        }
    }

    public GradleInvocationParameters receiveSyncParameters(Duration timeout) {
        synchronized (lock) {
            return protocolHandler.receive(GradleInvocationParameters.class, timeout);
        }
    }

    public StudioAgentConnectionParameters receiveConnectionParameters(Duration timeout) {
        synchronized (lock) {
            return protocolHandler.receive(StudioAgentConnectionParameters.class, timeout);
        }
    }

    public StudioRequest receiveStudioRequest(Duration timeout) {
        synchronized (lock) {
            return protocolHandler.receive(StudioRequest.class, timeout);
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            connection.close();
        }
    }
}

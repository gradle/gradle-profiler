package org.gradle.profiler.client.protocol;

import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.client.protocol.messages.SyncCompleted;
import org.gradle.profiler.client.protocol.messages.SyncStarted;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * A singleton that runs inside a client process to communicate with the controller process.
 */
public enum Client {
    INSTANCE;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Object lock = new Object();
    private Connection connection;
    private Serializer serializer;

    public void connect(int port) {
        synchronized (lock) {
            if (connection != null) {
                throw new IllegalStateException("This client is already connected.");
            }
            try {
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                connection = new Connection(socket);
                serializer = new Serializer("controller process", connection);
            } catch (IOException e) {
                throw new RuntimeException("Could not connect to controller process.", e);
            }
        }
    }

    public void send(SyncStarted message) {
        synchronized (lock) {
            serializer.send(message);
        }
    }

    public void send(SyncCompleted message) {
        synchronized (lock) {
            serializer.send(message);
        }
    }

    public void send(StudioSyncRequestCompleted message) {
        synchronized (lock) {
            serializer.send(message);
        }
    }

    public SyncParameters receiveSyncParameters(Duration timeout) {
        synchronized (lock) {
            return serializer.receive(SyncParameters.class, timeout);
        }
    }

    public ConnectionParameters receiveConnectionParameters(Duration timeout) {
        synchronized (lock) {
            return serializer.receive(ConnectionParameters.class, timeout);
        }
    }

    public StudioRequest receiveStudioRequest(Duration timeout) {
        synchronized (lock) {
            return serializer.receive(StudioRequest.class, timeout);
        }
    }

    public void listenAsync(Consumer<Client> runnable) {
        executor.execute(() -> runnable.accept(this));
    }

    public void disconnect() throws IOException {
        synchronized (lock) {
            try {
                if (connection != null) {
                    connection.close();
                }
            } finally {
                connection = null;
            }
        }
    }
}

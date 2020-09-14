package org.gradle.profiler.client.protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * A singleton that runs inside a client process to communicate with the controller process.
 */
public class Client {
    public static final Client INSTANCE = new Client();

    private final Object lock = new Object();
    private Connection connection;
    private Serializer serializer;

    public void connect(int port) throws IOException {
        synchronized (lock) {
            if (connection != null) {
                throw new IllegalStateException("This client is already connected.");
            }
            Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
            connection = new Connection(socket);
            serializer = new Serializer("controller process", connection);
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

package org.gradle.profiler.client.protocol;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

public class ServerConnection implements Closeable {
    private final Object lock = new Object();
    private final String peerName;
    private final Connection connection;
    private Thread receiveThread;

    public ServerConnection(String peerName, Connection connection) {
        this.peerName = peerName;
        this.connection = connection;
    }

    public void receive(Consumer<Message> consumer) {
        synchronized (lock) {
            if (receiveThread != null) {
                throw new IllegalStateException("Already receiving on another thread.");
            }
            receiveThread = new Thread(() -> {
                Serializer serializer = new Serializer(peerName, connection);
                while (true) {
                    Message message = serializer.receive();
                    if (message == null) {
                        // Disconnected
                        System.out.println(String.format("* %s has disconnected.", peerName));
                        return;
                    }
                    consumer.accept(message);
                }
            });
            receiveThread.start();
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            if (receiveThread != null) {
                try {
                    receiveThread.join();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
            receiveThread = null;
        }
        connection.close();
    }
}

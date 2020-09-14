package org.gradle.profiler.client.protocol;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An endpoint for communicating with a single client process.
 */
public class Server implements Closeable {

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final List<ServerConnection> connections = new ArrayList<>();
    private final String peerName;

    public Server(String peerName) {
        this.peerName = peerName;
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress((InetAddress) null, 0));
            serverSocketChannel.configureBlocking(false);
            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not start listening for incoming %s connections.", peerName), e);
        }
    }

    public int getPort() {
        try {
            return ((InetSocketAddress) serverSocketChannel.getLocalAddress()).getPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not determine local port.", e);
        }
    }

    public ServerConnection waitForIncoming() {
        try {
            int keys = selector.select(TimeUnit.MINUTES.toMillis(2));
            if (keys != 1) {
                throw new IllegalStateException(String.format("Timeout waiting for incoming connection from %s.", peerName));
            }
            SocketChannel channel = serverSocketChannel.accept();
            ServerConnection connection = new ServerConnection(peerName, new Connection(channel.socket()));
            connections.add(connection);
            return connection;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not receive incoming connection from %s.", peerName), e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            for (ServerConnection connection : connections) {
                connection.close();
            }
            serverSocketChannel.close();
            selector.close();
        } finally {
            connections.clear();
        }
    }
}

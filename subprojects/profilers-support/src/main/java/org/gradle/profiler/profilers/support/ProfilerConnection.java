package org.gradle.profiler.profilers.support;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * A connection between the profiler and a build process, over which they exchange {@link ProfilerMessage}s.
 *
 * <p>Each message is written as its type name, followed by its fields.</p>
 */
public final class ProfilerConnection implements Closeable {
    private final Closeable resource;
    private final DataInputStream input;
    private final DataOutputStream output;

    public ProfilerConnection(Socket socket) throws IOException {
        this(socket, socket.getInputStream(), socket.getOutputStream());
    }

    ProfilerConnection(Closeable resource, InputStream input, OutputStream output) {
        this.resource = resource;
        this.input = new DataInputStream(input);
        this.output = new DataOutputStream(output);
    }

    public void send(ProfilerMessage message) throws IOException {
        if (message instanceof BuildFinished) {
            output.writeUTF("BuildFinished");
            output.writeUTF(((BuildFinished) message).getPid());
        } else if (message instanceof RecordingStopped) {
            output.writeUTF("RecordingStopped");
        } else {
            throw new IllegalArgumentException("Unsupported message " + message + ".");
        }
        output.flush();
    }

    /**
     * Waits for the next message, which must be of the given type.
     *
     * @throws EOFException when the other side closes the connection before sending a message
     */
    public <T extends ProfilerMessage> T receive(Class<T> type) throws IOException {
        ProfilerMessage message = read();
        if (!type.isInstance(message)) {
            throw new IllegalStateException("Expected " + type.getSimpleName() + " but received " + message + ".");
        }
        return type.cast(message);
    }

    private ProfilerMessage read() throws IOException {
        String type = input.readUTF();
        switch (type) {
            case "BuildFinished":
                return new BuildFinished(input.readUTF());
            case "RecordingStopped":
                return new RecordingStopped();
            default:
                throw new IOException("Received unknown message " + type + ".");
        }
    }

    @Override
    public void close() throws IOException {
        resource.close();
    }
}

package org.gradle.profiler.client.protocol;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Connection implements Closeable {
    private final Socket socket;
    private final DataOutputStream outputStream;
    private final DataInputStream inputStream;

    public Connection(Socket socket) throws IOException {
        this.socket = socket;
        outputStream = new DataOutputStream(socket.getOutputStream());
        inputStream = new DataInputStream(socket.getInputStream());
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    public byte readByte() throws IOException {
        return inputStream.readByte();
    }

    public int readInt() throws IOException {
        return inputStream.readInt();
    }

    public long readLong() throws IOException {
        return inputStream.readLong();
    }

    public void writeByte(byte value) throws IOException {
        outputStream.writeByte(value);
    }

    public void writeInt(int value) throws IOException {
        outputStream.writeInt(value);
    }

    public void writeLong(long value) throws IOException {
        outputStream.writeLong(value);
    }

    public void flush() throws IOException {
        outputStream.flush();
        socket.getOutputStream().flush();
    }
}

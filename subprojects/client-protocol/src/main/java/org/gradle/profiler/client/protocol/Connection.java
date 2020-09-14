package org.gradle.profiler.client.protocol;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

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

    public void writeByte(byte value) throws IOException {
        outputStream.writeByte(value);
    }

    public int readInt() throws IOException {
        return inputStream.readInt();
    }

    public void writeInt(int value) throws IOException {
        outputStream.writeInt(value);
    }

    public long readLong() throws IOException {
        return inputStream.readLong();
    }

    public void writeLong(long value) throws IOException {
        outputStream.writeLong(value);
    }

    public String readString() throws IOException {
        return inputStream.readUTF();
    }

    public void writeString(String value) throws IOException {
        outputStream.writeUTF(value);
    }

    public List<String> readStrings() throws IOException {
        int count = inputStream.readInt();
        List<String> strings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            strings.add(inputStream.readUTF());
        }
        return strings;
    }

    public void writeStrings(List<String> strings) throws IOException {
        outputStream.writeInt(strings.size());
        for (String s : strings) {
            outputStream.writeUTF(s);
        }
    }

    public void flush() throws IOException {
        outputStream.flush();
        socket.getOutputStream().flush();
    }
}

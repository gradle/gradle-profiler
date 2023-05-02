package org.gradle.profiler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

class TeeOutputStream extends OutputStream {
    private final List<OutputStream> targets;

    public TeeOutputStream(OutputStream... targets) {
        this.targets = Arrays.asList(targets);
    }

    @Override
    public void write(int b) throws IOException {
        withAll(stream -> stream.write(b));
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        withAll(stream -> stream.write(b, off, len));
    }

    @Override
    public void flush() throws IOException {
        withAll(stream -> stream.flush());
    }

    @Override
    public void close() throws IOException {
        withAll(stream -> stream.close());
    }

    private void withAll(IOAction action) throws IOException {
        IOException failure = null;
        for (OutputStream target : targets) {
            try {
                action.withStream(target);
            } catch (IOException e) {
                failure = e;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private interface IOAction {
        void withStream(OutputStream stream) throws IOException;
    }
}

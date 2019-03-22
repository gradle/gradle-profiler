package org.gradle.trace.stream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Writes (immutable) values of type {@link T} to a file.
 */
public class AsyncWriter<T> {
    private final Object EOS = new Object();
    private final BlockingQueue<Object> eventQueue = new LinkedBlockingQueue<>();
    private final File outFile;
    private final Renderer<T> renderer;
    private final Thread thread;

    public AsyncWriter(File outFile, Renderer<T> renderer) {
        this.outFile = outFile;
        this.renderer = renderer;
        thread = new Thread(this::run);
        thread.start();
    }

    public void append(T value) {
        eventQueue.add(value);
    }

    public void finished() {
        eventQueue.add(EOS);
    }

    public void stop() {
        finished();
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void run() {
        try {
            PrintWriter writer = new PrintWriter(new FileOutputStream(outFile));
            try {
                renderer.header(writer);
                while (true) {
                    Object next = eventQueue.take();
                    if (next == EOS) {
                        break;
                    }
                    T value = (T) next;
                    renderer.write(value, writer);
                }
                renderer.footer(writer);
            } finally {
                writer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface Renderer<T> {
        default void header(PrintWriter writer) {
        }

        void write(T value, PrintWriter writer);

        default void footer(PrintWriter writer) {
        }
    }
}

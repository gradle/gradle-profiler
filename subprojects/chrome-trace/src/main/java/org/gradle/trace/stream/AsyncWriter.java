package org.gradle.trace.stream;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Writes (immutable) values of type {@link T} to a file.
 */
public class AsyncWriter<T> {
    private final Object EOS = new Object();
    private final BlockingQueue<Object> eventQueue = new LinkedBlockingQueue<>();
    private final Path outPath;
    private final Renderer<T> renderer;
    private final Thread thread;

    public AsyncWriter(Path outPath, Renderer<T> renderer) {
        this.outPath = outPath;
        this.renderer = renderer;
        thread = new Thread(this::run);
        thread.start();
    }

    public AsyncWriter(File outPath, Renderer<T> renderer) {
        this(outPath.toPath(), renderer);
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
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outPath, StandardCharsets.UTF_8))) {
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

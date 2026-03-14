package org.gradle.profiler.flamegraph;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * A reusable byte buffer for reading lines from an {@link InputStream} one at a time,
 * without per-line heap allocation. Extends {@link ByteArrayOutputStream} to access the
 * protected internal array and count directly, avoiding the copy made by {@link #toByteArray()}.
 */
public class LineBuffer extends ByteArrayOutputStream {

    /**
     * Resets this buffer and reads the next newline-terminated line from {@code input}.
     *
     * @return true if a line was read, false if the stream is exhausted
     */
    public boolean readFrom(InputStream input) throws IOException {
        reset();
        int b = input.read();
        if (b == -1) {
            return false;
        }
        while (b != -1) {
            write(b);
            if (b == '\n') {
                break;
            }
            b = input.read();
        }
        return true;
    }

    /**
     * Returns true if this buffer's content, after stripping leading and trailing
     * ASCII whitespace, is byte-for-byte equal to {@code target}.
     */
    public boolean trimmedEquals(byte[] target) {
        int start = trimmedStart();
        int end = trimmedEnd(start);
        if (end - start != target.length) {
            return false;
        }
        for (int i = 0; i < target.length; i++) {
            if (buf[start + i] != target[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if this buffer's content, after stripping leading and trailing
     * ASCII whitespace, starts with {@code prefix}.
     */
    public boolean trimmedStartsWith(byte[] prefix) {
        int start = trimmedStart();
        int end = trimmedEnd(start);
        if (end - start < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (buf[start + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns this buffer's content, after stripping leading and trailing ASCII whitespace,
     * decoded using the given {@code charset}.
     */
    public String trimmedString(Charset charset) {
        int start = trimmedStart();
        int end = trimmedEnd(start);
        return new String(buf, start, end - start, charset);
    }

    private int trimmedStart() {
        int start = 0;
        while (start < count && buf[start] <= ' ') {
            start++;
        }
        return start;
    }

    private int trimmedEnd(int start) {
        int end = count;
        while (end > start && buf[end - 1] <= ' ') {
            end--;
        }
        return end;
    }

}

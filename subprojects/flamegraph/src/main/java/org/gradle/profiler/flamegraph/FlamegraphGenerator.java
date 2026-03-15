package org.gradle.profiler.flamegraph;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Generates a self-contained flamegraph HTML file by embedding stacks data into
 * the application bundle template. Stacks files are gzip-compressed and Base64-encoded
 * inline as {@code <template>} elements, allowing the viewer standalone from disk without
 * a server.
 */
public class FlamegraphGenerator {

    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    /**
     * The line in the application bundle to place embedded stacks before.
     */
    private static final byte[] TARGET_LINE_BYTES = "</body>".getBytes(StandardCharsets.UTF_8);

    /**
     * Chunk size for parallel compression .
     */
    private static final int CHUNK_SIZE = 32 * 1024 * 1024;

    /**
     * Location in jar of the application bundle template to embed stacks files into.
     */
    private static final String FLAMEGRAPH_HTML_TEMPLATE_PATH = "/org/gradle/profiler/flamegraph/index.html";

    /**
     * Minimal GZIP header (RFC 1952): magic, deflate method, no flags, zero mtime, no extra flags, unknown OS.
     */
    private static final byte[] GZIP_HEADER = {
        (byte) 0x1f, (byte) 0x8b,  // magic number
        8,                           // compression method: deflate
        0,                           // flags: none
        0, 0, 0, 0,                  // mtime: 0
        0,                           // extra flags: none
        (byte) 0xff                  // OS: unknown
    };

    /**
     * Command-line entry point. Accepts one or more absolute stacks file paths followed by
     * the destination path as the final argument.
     *
     * @param args absolute stacks file paths followed by the output destination path
     * @throws IOException if an I/O error occurs
     */
    public static void main(String... args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: FlamegraphGenerator <stacks-file>... <destination>");
        }
        List<Path> stacksFiles = Arrays.stream(args, 0, args.length - 1)
            .map(Path::of)
            .collect(Collectors.toList());
        Path destination = Path.of(args[args.length - 1]);
        new FlamegraphGenerator().generate(stacksFiles, destination);
    }

    /**
     * Generates a self-contained flamegraph HTML file at {@code destination} by embedding
     * the given stacks files into the application bundle template.
     *
     * @param stacksFiles absolute paths to the stacks files to embed; must have distinct file names
     * @param destination path of the output HTML file to create
     */
    public void generate(List<Path> stacksFiles, Path destination) throws IOException {
        for (Path stacksFile : stacksFiles) {
            if (!stacksFile.isAbsolute()) {
                throw new IllegalArgumentException("Stacks file '" + stacksFile + "' must be absolute");
            }
            if (!Files.isRegularFile(stacksFile)) {
                throw new IllegalArgumentException("Stacks file " + stacksFile + " does not exist");
            }
        }

        long distinctNameCount = stacksFiles.stream().map(p -> p.getFileName().toString()).distinct().count();
        if (distinctNameCount != stacksFiles.size()) {
            throw new IllegalArgumentException("Stacks files must have distinct names");
        }

        if (destination.getFileName() == null || !destination.getFileName().toString().endsWith(".html")) {
            throw new IllegalArgumentException("Destination file must be an HTML file");
        }

        InputStream template = FlamegraphGenerator.class.getResourceAsStream(FLAMEGRAPH_HTML_TEMPLATE_PATH);
        if (template == null) {
            throw new IllegalStateException("Could not find application bundle template on classpath");
        }

        Files.createDirectories(destination.getParent());

        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(destination));
             BufferedInputStream input = new BufferedInputStream(template)
        ) {
            boolean foundTargetLine = false;
            LineBuffer lineBuffer = new LineBuffer();

            while (!foundTargetLine) {
                if (!lineBuffer.readFrom(input)) {
                    break;
                }

                if (lineBuffer.trimmedEquals(TARGET_LINE_BYTES)) {
                    foundTargetLine = true;

                    String names = stacksFiles.stream()
                        .map(p -> toBase64(p.getFileName().toString()))
                        .collect(Collectors.joining(","));
                    writeUtf8(output, "<template id=\"embedded-stacks-names\">\n");
                    writeUtf8(output, names);
                    writeUtf8(output, "\n</template>\n");

                    for (int i = 0; i < stacksFiles.size(); i++) {
                        writeUtf8(output, "<template id=\"embedded-stacks-" + i + "\">\n");
                        embedStacksParallel(stacksFiles.get(i), output);
                        writeUtf8(output, "\n</template>\n");
                    }
                }

                lineBuffer.writeTo(output);
            }

            if (!foundTargetLine) {
                throw new IllegalStateException("Could not find </body> in application bundle template");
            }

            input.transferTo(output);
        }
    }

    /**
     * Compresses {@code stacksFile} into a single GZIP stream written Base64-encoded to {@code output}.
     * <p>
     * The file is split into {@value #CHUNK_SIZE}-byte chunks. Each chunk is compressed in parallel
     * using its own {@link Deflater} instance (raw deflate, no wrapper). Non-final chunks are flushed
     * with {@link Deflater#FULL_FLUSH}, which byte-aligns the output and emits a BFINAL=0 sync block,
     * making them safe to concatenate. The final chunk is finished normally (BFINAL=1). The combined
     * raw deflate payload is wrapped in a single GZIP header/footer, producing one valid GZIP stream
     * that browsers can decompress with {@code DecompressionStream("gzip")}.
     */
    private static void embedStacksParallel(Path stacksFile, OutputStream output) throws IOException {
        int threadCount = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            // NonClosingOutputStream prevents the Base64 encoder's close() from closing `output`,
            // which we still need to write to after this method returns. Closing the Base64 encoder
            // is important as it has finalization work (padding) to perform.
            try (OutputStream base64Out = ENCODER.wrap(new NonClosingOutputStream(output))) {
                base64Out.write(GZIP_HEADER);

                CRC32 crc32 = new CRC32();
                long totalSize = 0;
                ArrayDeque<Future<byte[]>> inFlight = new ArrayDeque<>();
                byte[] readBuf = new byte[CHUNK_SIZE];

                try (InputStream in = new BufferedInputStream(Files.newInputStream(stacksFile))) {
                    // Read one chunk ahead so we know which chunk is the last (needs FINISH, not FULL_FLUSH).
                    int read = in.readNBytes(readBuf, 0, CHUNK_SIZE);
                    byte[] currentChunk = read > 0 ? Arrays.copyOf(readBuf, read) : new byte[0];

                    while (true) {
                        int nextRead = in.readNBytes(readBuf, 0, CHUNK_SIZE);
                        boolean isLast = nextRead == 0;

                        crc32.update(currentChunk);
                        totalSize += currentChunk.length;

                        while (inFlight.size() >= threadCount) {
                            drainNext(inFlight, base64Out);
                        }
                        final byte[] chunk = currentChunk;
                        inFlight.add(executor.submit(() -> deflateChunk(chunk, isLast)));

                        if (isLast) {
                            break;
                        }
                        currentChunk = Arrays.copyOf(readBuf, nextRead);
                    }
                }

                while (!inFlight.isEmpty()) {
                    drainNext(inFlight, base64Out);
                }

                writeGzipFooter(base64Out, crc32.getValue(), totalSize);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void drainNext(ArrayDeque<Future<byte[]>> inFlight, OutputStream out) throws IOException {
        try {
            out.write(inFlight.poll().get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during parallel compression", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Parallel compression failed", cause);
        }
    }

    /**
     * Compresses {@code data} using raw deflate (no GZIP wrapper).
     * <p>
     * Non-final chunks use {@link Deflater#FULL_FLUSH}: output is byte-aligned and ends with a
     * BFINAL=0 sync block, so the next chunk's deflate output can be appended directly.
     * The final chunk uses {@link Deflater#finish()}: output ends with a BFINAL=1 block.
     */
    private static byte[] deflateChunk(byte[] data, boolean isFinal) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true); // nowrap=true: raw deflate, no zlib header
        deflater.setInput(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length / 4 + 64);
        byte[] buf = new byte[65536];
        try {
            if (isFinal) {
                deflater.finish();
                while (!deflater.finished()) {
                    int n = deflater.deflate(buf);
                    if (n > 0) baos.write(buf, 0, n);
                }
            } else {
                int n;
                do {
                    n = deflater.deflate(buf, 0, buf.length, Deflater.FULL_FLUSH);
                    if (n > 0) baos.write(buf, 0, n);
                } while (n == buf.length);
            }
        } finally {
            deflater.end();
        }
        return baos.toByteArray();
    }

    /** Writes the 8-byte GZIP footer: CRC32 and uncompressed size, both little-endian. */
    private static void writeGzipFooter(OutputStream out, long crc32Value, long totalSize) throws IOException {
        byte[] footer = new byte[8];
        footer[0] = (byte) (crc32Value);
        footer[1] = (byte) (crc32Value >> 8);
        footer[2] = (byte) (crc32Value >> 16);
        footer[3] = (byte) (crc32Value >> 24);
        footer[4] = (byte) (totalSize);
        footer[5] = (byte) (totalSize >> 8);
        footer[6] = (byte) (totalSize >> 16);
        footer[7] = (byte) (totalSize >> 24);
        out.write(footer);
    }

    private static void writeUtf8(OutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String toBase64(String text) {
        return ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * An {@link OutputStream} wrapper that delegates to another stream,
     * but suppresses {@link #close()}.
     */
    private static class NonClosingOutputStream extends OutputStream {

        private final OutputStream delegate;

        NonClosingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void close() {
            // Do nothing
        }

    }

}

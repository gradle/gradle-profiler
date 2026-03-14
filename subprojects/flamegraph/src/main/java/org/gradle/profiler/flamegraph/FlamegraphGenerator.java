package org.gradle.profiler.flamegraph;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * Generates a self-contained flamegraph HTML file by embedding stacks data into
 * the application bundle template. Stacks files are gzip-compressed and Base64-encoded
 * inline as {@code <template>} elements, allowing the viewer standalone from disk without
 * a server.
 */
public class FlamegraphGenerator {

    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    private static final byte[] TARGET_LINE_BYTES = "</body>".getBytes(StandardCharsets.UTF_8);

    private static final String FLAMEGRAPH_HTML_TEMPLATE_PATH = "/org/gradle/profiler/flamegraph/index.html";

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
                        try (InputStream stackStream = new BufferedInputStream(Files.newInputStream(stacksFiles.get(i)));
                            // NonClosingOutputStream prevents GZIPOutputStream.close() from closing `output`,
                            // which we still need to write to after this try-with-resources block completes.
                            // It is important that we close the GZIPOutputStream and wrapped Base64 encoding
                            // stream, as they both have finalization work to perform after all input bytes are
                            // given.
                            GZIPOutputStream gzip = new GZIPOutputStream(ENCODER.wrap(new NonClosingOutputStream(output)))
                        ) {
                            stackStream.transferTo(gzip);
                        }
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

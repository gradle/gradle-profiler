package org.gradle.profiler.perfetto.jfr;

import com.google.protobuf.CodedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import perfetto.protos.Trace;
import perfetto.protos.TracePacket;

/**
 * Serializes Perfetto packets to the protobuf trace stream expected by the UI and tooling.
 *
 * <p>It also emits the initial packet that clears incremental state for the shared packet sequence.
 */
public final class PerfettoTraceWriter implements AutoCloseable {
    static final int PACKET_SEQUENCE_ID = 1;

    private final OutputStream outputStream;
    private final CodedOutputStream codedOutput;

    public PerfettoTraceWriter(Path outputFile) throws IOException {
        outputStream = Files.newOutputStream(outputFile);
        codedOutput = CodedOutputStream.newInstance(outputStream);
        write(TracePacket.newBuilder()
            .setTrustedPacketSequenceId(PACKET_SEQUENCE_ID)
            .setSequenceFlags(TracePacket.SequenceFlags.SEQ_INCREMENTAL_STATE_CLEARED_VALUE)
            .build());
    }

    public void write(TracePacket packet) throws IOException {
        codedOutput.writeMessage(Trace.PACKET_FIELD_NUMBER, packet);
    }

    @Override
    public void close() throws IOException {
        codedOutput.flush();
        outputStream.close();
    }
}

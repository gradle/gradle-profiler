package org.gradle.profiler.client.protocol.serialization;

import org.gradle.profiler.client.protocol.Connection;
import org.gradle.profiler.client.protocol.messages.Message;

import java.io.IOException;

/**
 * Message reader writer that reads and writes messages from and to a {@link Connection}.
 */
public interface MessageReaderWriter {

    /**
     * Reads a message from the given connection.
     */
    Message readFrom(Connection connection) throws IOException;

    /**
     * Writes a message to the given connection.
     */
    void writeTo(Connection connection, Message message) throws IOException;

}

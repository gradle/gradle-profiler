package org.gradle.profiler.client.protocol.serialization;

import org.gradle.profiler.client.protocol.Connection;
import org.gradle.profiler.client.protocol.messages.*;
import org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Registry that contains classes to id mappings and all message mappers.
 */
public enum MessageRegistry {
    SYNC_STARTED((byte) 1, SyncStarted.class, new MessageReaderWriter() {
        @Override
        public void writeTo(Connection connection, Message message) throws IOException {
            SyncStarted syncStarted = (SyncStarted) message;
            connection.writeInt(syncStarted.getId());
        }

        @Override
        public Message readFrom(Connection connection) throws IOException {
            int startId = connection.readInt();
            return new SyncStarted(startId);
        }
    }),
    SYNC_COMPLETED((byte) 2, SyncCompleted.class, new MessageReaderWriter() {
        @Override
        public void writeTo(Connection connection, Message message) throws IOException {
            SyncCompleted syncCompleted = (SyncCompleted) message;
            connection.writeInt(syncCompleted.getId());
            connection.writeLong(syncCompleted.getDurationMillis());
        }

        @Override
        public Message readFrom(Connection connection) throws IOException {
            int completeId = connection.readInt();
            long durationMillis = connection.readLong();
            return new SyncCompleted(completeId, durationMillis);
        }
    }),
    SYNC_PARAMETERS((byte) 3, SyncParameters.class, new MessageReaderWriter() {
        @Override
        public void writeTo(Connection connection, Message message) throws IOException {
            SyncParameters syncParameters = (SyncParameters) message;
            connection.writeStrings(syncParameters.getGradleArgs());
            connection.writeStrings(syncParameters.getJvmArgs());
        }

        @Override
        public Message readFrom(Connection connection) throws IOException {
            List<String> gradleArgs = connection.readStrings();
            List<String> jvmArgs = connection.readStrings();
            return new SyncParameters(gradleArgs, jvmArgs);
        }
    }),
    CONNECTION_PARAMETERS((byte) 4, ConnectionParameters.class, new MessageReaderWriter() {
        @Override
        public void writeTo(Connection connection, Message message) throws IOException {
            ConnectionParameters connectionParameters = (ConnectionParameters) message;
            connection.writeString(connectionParameters.getGradleInstallation().getPath());
        }
        @Override
        public Message readFrom(Connection connection) throws IOException {
            String gradleHome = connection.readString();
            return new ConnectionParameters(new File(gradleHome));
        }
    }),
    STUDIO_REQUEST((byte) 5, StudioRequest.class, new MessageReaderWriter() {
        @Override
        public void writeTo(Connection connection, Message message) throws IOException {
            StudioRequest request = (StudioRequest) message;
            connection.writeInt(request.getId());
            connection.writeString(request.getType().toString());
        }

        @Override
        public Message readFrom(Connection connection) throws IOException {
            int syncId = connection.readInt();
            StudioRequestType requestType = StudioRequestType.valueOf(connection.readString());
            return new StudioRequest(syncId, requestType);
        }
    }),
    STUDIO_SYNC_REQUEST((byte) 6, StudioSyncRequestCompleted.class, new MessageReaderWriter() {
        @Override
        public void writeTo(Connection connection, Message message) throws IOException {
            StudioSyncRequestCompleted request = (StudioSyncRequestCompleted) message;
            connection.writeInt(request.getId());
            connection.writeLong(request.getDurationMillis());
            connection.writeString(request.getResult().toString());
        }

        @Override
        public Message readFrom(Connection connection) throws IOException {
            int syncRequestCompletedId = connection.readInt();
            long syncRequestCompletedDurationMillis = connection.readLong();
            StudioSyncRequestResult result = StudioSyncRequestResult.valueOf(connection.readString());
            return new StudioSyncRequestCompleted(syncRequestCompletedId, syncRequestCompletedDurationMillis, result);
        }
    })
    ;

    private final byte messageId;
    private final Class<?> type;
    private final MessageReaderWriter readerWriter;

    MessageRegistry(byte id, Class<?> type, MessageReaderWriter readerWriter) {
        this.messageId = id;
        this.type = type;
        this.readerWriter = readerWriter;
    }

    public static byte findMessageId(Class<?> messageClass) {
        MessageRegistry registry = Arrays.stream(values()).filter(it -> it.type.equals(messageClass))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No message id is declared in the " + MessageRegistry.class + " for message class: " + messageClass));
        return registry.messageId;
    }

    public static MessageReaderWriter findMessageReaderWriter(Class<?> messageClass) {
        MessageRegistry registry = Arrays.stream(values()).filter(it -> it.type.equals(messageClass))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No message reader writer is declared in the " + MessageRegistry.class + " for message class: " + messageClass));
        return registry.readerWriter;
    }

    public static MessageReaderWriter findMessageReaderWriter(byte messageId) {
        MessageRegistry registry = Arrays.stream(values()).filter(it -> it.messageId == messageId)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No message reader writer is declared in the " + MessageRegistry.class + " for message id: " + messageId));
        return registry.readerWriter;
    }

}

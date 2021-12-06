package org.gradle.profiler.client.protocol.serialization;

import org.gradle.profiler.client.protocol.Connection;
import org.gradle.profiler.client.protocol.messages.GradleInvocationCompleted;
import org.gradle.profiler.client.protocol.messages.GradleInvocationParameters;
import org.gradle.profiler.client.protocol.messages.GradleInvocationStarted;
import org.gradle.profiler.client.protocol.messages.Message;
import org.gradle.profiler.client.protocol.messages.StudioAgentConnectionParameters;
import org.gradle.profiler.client.protocol.messages.StudioCacheCleanupCompleted;
import org.gradle.profiler.client.protocol.messages.StudioRequest;
import org.gradle.profiler.client.protocol.messages.StudioRequest.StudioRequestType;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted;
import org.gradle.profiler.client.protocol.messages.StudioSyncRequestCompleted.StudioSyncRequestResult;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Message serializers that read and write messages from and to a {@link Connection}.
 */
public enum MessageSerializer {
    GRADLE_INVOCATION_STARTED((byte) 1, GradleInvocationStarted.class) {
        @Override
        public void doWriteTo(Connection connection, Message message) throws IOException {
            GradleInvocationStarted gradleInvocationStarted = (GradleInvocationStarted) message;
            connection.writeInt(gradleInvocationStarted.getId());
        }

        @Override
        public Message doReadFrom(Connection connection, int bodyTimeoutMillis) throws IOException {
            int startId = connection.readInt(bodyTimeoutMillis);
            return new GradleInvocationStarted(startId);
        }
    },
    GRADLE_INVOCATION_COMPLETED((byte) 2, GradleInvocationCompleted.class) {
        @Override
        public void doWriteTo(Connection connection, Message message) throws IOException {
            GradleInvocationCompleted gradleInvocationCompleted = (GradleInvocationCompleted) message;
            connection.writeInt(gradleInvocationCompleted.getId());
            connection.writeLong(gradleInvocationCompleted.getDurationMillis());
        }

        @Override
        public Message doReadFrom(Connection connection, int bodyTimeoutMillis) throws IOException {
            int completeId = connection.readInt(bodyTimeoutMillis);
            long durationMillis = connection.readLong(bodyTimeoutMillis);
            return new GradleInvocationCompleted(completeId, durationMillis);
        }
    },
    GRADLE_INVOCATION_PARAMETERS((byte) 3, GradleInvocationParameters.class) {
        @Override
        public void doWriteTo(Connection connection, Message message) throws IOException {
            GradleInvocationParameters gradleInvocationParameters = (GradleInvocationParameters) message;
            connection.writeStrings(gradleInvocationParameters.getGradleArgs());
            connection.writeStrings(gradleInvocationParameters.getJvmArgs());
        }

        @Override
        public Message doReadFrom(Connection connection, int bodyTimeoutMillis) throws IOException {
            List<String> gradleArgs = connection.readStrings(bodyTimeoutMillis);
            List<String> jvmArgs = connection.readStrings(bodyTimeoutMillis);
            return new GradleInvocationParameters(gradleArgs, jvmArgs);
        }
    },
    STUDIO_AGENT_CONNECTION_PARAMETERS((byte) 4, StudioAgentConnectionParameters.class) {
        @Override
        public void doWriteTo(Connection connection, Message message) throws IOException {
            StudioAgentConnectionParameters studioAgentConnectionParameters = (StudioAgentConnectionParameters) message;
            connection.writeString(studioAgentConnectionParameters.getGradleInstallation().getPath());
        }
        @Override
        public Message doReadFrom(Connection connection, int bodyTimeoutMillis) throws IOException {
            String gradleHome = connection.readString(bodyTimeoutMillis);
            return new StudioAgentConnectionParameters(new File(gradleHome));
        }
    },
    STUDIO_REQUEST((byte) 5, StudioRequest.class) {
        @Override
        public void doWriteTo(Connection connection, Message message) throws IOException {
            StudioRequest request = (StudioRequest) message;
            connection.writeInt(request.getId());
            connection.writeString(request.getType().toString());
        }

        @Override
        public Message doReadFrom(Connection connection, int bodyTimeoutMillis) throws IOException {
            int syncId = connection.readInt(bodyTimeoutMillis);
            StudioRequestType requestType = StudioRequestType.valueOf(connection.readString(bodyTimeoutMillis));
            return new StudioRequest(syncId, requestType);
        }
    },
    STUDIO_SYNC_REQUEST_COMPLETED((byte) 6, StudioSyncRequestCompleted.class) {
        @Override
        public void doWriteTo(Connection connection, Message message) throws IOException {
            StudioSyncRequestCompleted request = (StudioSyncRequestCompleted) message;
            connection.writeInt(request.getId());
            connection.writeLong(request.getDurationMillis());
            connection.writeString(request.getResult().toString());
            connection.writeString(request.getErrorMessage());
        }

        @Override
        public Message doReadFrom(Connection connection, int bodyTimeoutMillis) throws IOException {
            int syncRequestCompletedId = connection.readInt(bodyTimeoutMillis);
            long syncRequestCompletedDurationMillis = connection.readLong(bodyTimeoutMillis);
            StudioSyncRequestResult result = StudioSyncRequestResult.valueOf(connection.readString(bodyTimeoutMillis));
            String failureReason = connection.readString(bodyTimeoutMillis);
            return new StudioSyncRequestCompleted(syncRequestCompletedId, syncRequestCompletedDurationMillis, result, failureReason);
        }
    },
    STUDIO_CACHE_CLEANUP_COMPLETED((byte) 7, StudioCacheCleanupCompleted.class) {
        @Override
        public void doWriteTo(Connection connection, Message message) throws IOException {
            StudioCacheCleanupCompleted request = (StudioCacheCleanupCompleted) message;
            connection.writeInt(request.getId());
        }

        @Override
        public Message doReadFrom(Connection connection, int bodyTimeoutMillis) throws IOException {
            int cacheCompletedId = connection.readInt(bodyTimeoutMillis);
            return new StudioCacheCleanupCompleted(cacheCompletedId);
        }
    }
    ;

    private static final Map<Class<? extends Message>, MessageSerializer> SERIALIZERS_BY_CLASS = Collections.unmodifiableMap(
        Arrays.stream(values()).collect(Collectors.toMap(e -> e.type, Function.identity())));

    private static final Map<Byte, MessageSerializer> SERIALIZERS_BY_MESSAGE_ID = Collections.unmodifiableMap(
        Arrays.stream(values()).collect(Collectors.toMap(e -> e.messageId, Function.identity())));

    private final byte messageId;
    private final Class<? extends Message> type;

    MessageSerializer(byte id, Class<? extends Message> type) {
        this.messageId = id;
        this.type = type;
    }

    protected abstract Message doReadFrom(Connection connection, int bodyTimeoutMillis) throws IOException;
    protected abstract void doWriteTo(Connection connection, Message message) throws IOException;

    /**
     * Reads a message from the given connection.
     */
    public Message readFrom(Connection connection, int timeoutMillis) throws IOException {
        return doReadFrom(connection, timeoutMillis);
    }

    /**
     * Writes a message to the given connection.
     */
    public void writeTo(Connection connection, Message message) throws IOException {
        connection.writeByte(messageId);
        doWriteTo(connection, message);
    }

    public static MessageSerializer getMessageSerializer(Class<? extends Message> messageClass) {
        MessageSerializer serializer = SERIALIZERS_BY_CLASS.get(messageClass);
        if (serializer == null) {
            throw new IllegalArgumentException("No message reader writer is declared in the " + MessageSerializer.class + " for message class: " + messageClass);
        }
        return serializer;
    }

    public static MessageSerializer getMessageSerializer(byte messageId) {
        MessageSerializer serializer = SERIALIZERS_BY_MESSAGE_ID.get(messageId);
        if (serializer == null) {
            throw new IllegalArgumentException("No message reader writer is declared in the " + MessageSerializer.class + " for message class: " + messageId);
        }
        return serializer;
    }

}

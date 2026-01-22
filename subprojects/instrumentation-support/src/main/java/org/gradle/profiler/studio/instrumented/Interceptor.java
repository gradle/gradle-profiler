package org.gradle.profiler.studio.instrumented;

import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.agent.AgentClientContainer;
import org.gradle.profiler.client.protocol.messages.GradleInvocationParameters;
import org.gradle.profiler.client.protocol.messages.GradleInvocationStarted;
import org.gradle.profiler.client.protocol.messages.StudioAgentConnectionParameters;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Injected into Studio and called from instrumented classes.
 */
public class Interceptor {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Duration RECEIVE_CONNECTION_PARAMS_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration RECEIVE_SYNC_PARAMS_TIMEOUT = Duration.ofSeconds(60);

    static StudioAgentConnectionParameters connectionParameters;

    /**
     * Called when creating a connection.
     */
    public static void onConnect(DefaultGradleConnector connector) {
        System.out.println("* Creating project connection");
        if (connectionParameters == null) {
            connectionParameters = AgentClientContainer.INSTANCE.getClient().receiveConnectionParameters(RECEIVE_CONNECTION_PARAMS_TIMEOUT);
        }
        System.out.println("* Using Gradle home: " + connectionParameters.getGradleInstallation());
        connector.useInstallation(connectionParameters.getGradleInstallation());
    }

    /**
     * Called immediately prior to starting an operation.
     */
    public static ResultHandler<Object> onStartOperation(AbstractLongRunningOperation<?> operation, ResultHandler<Object> handler) {
        System.out.println("* Starting tooling API operation " + operation);
        int id = COUNTER.incrementAndGet();

        GradleInvocationParameters syncParameters;
        try {
            Client client = AgentClientContainer.INSTANCE.getClient();
            client.send(new GradleInvocationStarted(id));
            syncParameters = client.receiveSyncParameters(RECEIVE_SYNC_PARAMS_TIMEOUT);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            // Continue with original handler
            return handler;
        }

        System.out.println("* Using Gradle args: " + syncParameters.getGradleArgs());
        System.out.println("* Using JVM args: " + syncParameters.getJvmArgs());
        operation.addArguments(syncParameters.getGradleArgs());
        operation.addJvmArguments(syncParameters.getJvmArgs());
        return new RecordingResultHandler(handler, id, System.nanoTime());
    }
}

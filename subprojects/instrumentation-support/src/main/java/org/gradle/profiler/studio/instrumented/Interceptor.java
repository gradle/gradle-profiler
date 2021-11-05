package org.gradle.profiler.studio.instrumented;

import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.messages.StudioAgentConnectionParameters;
import org.gradle.profiler.client.protocol.messages.GradleInvocationParameters;
import org.gradle.profiler.client.protocol.messages.GradleInvocationStarted;
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

    static StudioAgentConnectionParameters connectionParameters;

    /**
     * Called when creating a connection.
     */
    public static void onConnect(DefaultGradleConnector connector) {
        System.out.println("* Creating project connection");
        if (connectionParameters == null) {
            connectionParameters = Client.INSTANCE.receiveConnectionParameters(Duration.ofSeconds(60));
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
            Client client = Client.INSTANCE;
            client.send(new GradleInvocationStarted(id));
            syncParameters = client.receiveSyncParameters(Duration.ofSeconds(300));
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

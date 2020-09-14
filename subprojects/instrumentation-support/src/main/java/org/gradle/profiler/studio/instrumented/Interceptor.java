package org.gradle.profiler.studio.instrumented;

import org.gradle.profiler.client.protocol.Client;
import org.gradle.profiler.client.protocol.ConnectionParameters;
import org.gradle.profiler.client.protocol.SyncParameters;
import org.gradle.profiler.client.protocol.SyncStarted;
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

    static ConnectionParameters connectionParameters;

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

        SyncParameters syncParameters;
        try {
            Client client = Client.INSTANCE;
            client.send(new SyncStarted(id));
            syncParameters = client.receiveSyncParameters(Duration.ofSeconds(60));
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

package org.gradle.profiler.studio.instrumented;

import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation;

/**
 * Injected into Studio and called from instrumented classes.
 */
public class Interceptor {
    /**
     * Called immediately prior to starting an operation.
     */
    public static ResultHandler<Object> onStartOperation(AbstractLongRunningOperation<?> operation, ResultHandler<Object> handler) {
        System.out.println("* Starting tooling API operation " + operation);
        return handler;
    }
}

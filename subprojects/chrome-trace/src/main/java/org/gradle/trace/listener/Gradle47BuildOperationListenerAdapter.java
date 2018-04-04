package org.gradle.trace.listener;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class Gradle47BuildOperationListenerAdapter implements BuildOperationListenerAdapter {
    private GradleInternal gradle;
    private BuildOperationListener listener;

    public Gradle47BuildOperationListenerAdapter(Gradle gradle, InvocationHandler invocationHandler) {
        this.gradle = (GradleInternal) gradle;
        register(invocationHandler);
    }

    private void register(InvocationHandler invocationHandler) {
        listener = (BuildOperationListener) Proxy.newProxyInstance(gradle.getClass().getClassLoader(), new Class[]{BuildOperationListener.class}, invocationHandler);
        getListenerManager().addListener(listener);
    }

    @Override
    public void remove() {
        getListenerManager().removeListener(listener);
    }

    private BuildOperationListenerManager getListenerManager() {
        return gradle.getServices().get(BuildOperationListenerManager.class);
    }
}

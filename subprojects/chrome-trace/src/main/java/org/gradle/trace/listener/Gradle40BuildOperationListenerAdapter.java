package org.gradle.trace.listener;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.BuildOperationListenerManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class Gradle40BuildOperationListenerAdapter implements BuildOperationListenerAdapter {
    private GradleInternal gradle;
    private BuildOperationListener listener;

    public Gradle40BuildOperationListenerAdapter(Gradle gradle, InvocationHandler invocationHandler) {
        this.gradle = (GradleInternal) gradle;
        register(invocationHandler);
    }

    private void register(InvocationHandler invocationHandler) {
        listener = (BuildOperationListener) Proxy.newProxyInstance(gradle.getClass().getClassLoader(), new Class[]{BuildOperationListener.class}, invocationHandler);
        BuildOperationListenerManager buildOperationListenerManager = gradle.getServices().get(BuildOperationListenerManager.class);
        buildOperationListenerManager.addListener(listener);
    }

    @Override
    public void remove() {
        BuildOperationListenerManager buildOperationListenerManager = gradle.getServices().get(BuildOperationListenerManager.class);
        buildOperationListenerManager.removeListener(listener);
    }
}

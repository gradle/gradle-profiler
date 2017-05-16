package org.gradle.trace.listener;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.BuildOperationService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class Gradle35BuildOperationListenerAdapter implements BuildOperationListenerAdapter {
    private GradleInternal gradle;
    private BuildOperationListener listener;

    public Gradle35BuildOperationListenerAdapter(Gradle gradle, InvocationHandler invocationHandler) {
        this.gradle = (GradleInternal) gradle;
        register(invocationHandler);
    }

    private void register(InvocationHandler invocationHandler) {
        listener = (BuildOperationListener) Proxy.newProxyInstance(gradle.getClass().getClassLoader(), new Class[]{BuildOperationListener.class}, invocationHandler);
        BuildOperationService buildOperationService = gradle.getServices().get(BuildOperationService.class);
        buildOperationService.addListener(listener);
    }

    @Override
    public void remove() {
        BuildOperationService buildOperationService = gradle.getServices().get(BuildOperationService.class);
        buildOperationService.removeListener(listener);
    }
}

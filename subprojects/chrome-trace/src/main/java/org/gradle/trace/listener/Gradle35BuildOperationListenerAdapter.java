package org.gradle.trace.listener;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.progress.BuildOperationListener;

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
        runBuildOperationServiceMethodForListener("addListener");
    }

    @Override
    public void remove() {
        runBuildOperationServiceMethodForListener("removeListener");
    }

    private void runBuildOperationServiceMethodForListener(String method) {
        try {
            Class<?> boServiceClass = Class.forName("org.gradle.internal.progress.BuildOperationService");
            Object buildOperationService = gradle.getServices().get(boServiceClass);
            buildOperationService.getClass().getMethod(method, BuildOperationListener.class).invoke(buildOperationService, listener);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

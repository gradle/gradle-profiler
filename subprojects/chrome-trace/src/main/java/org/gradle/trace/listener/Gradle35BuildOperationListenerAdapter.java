package org.gradle.trace.listener;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class Gradle35BuildOperationListenerAdapter implements BuildOperationListenerAdapter {
    private GradleInternal gradle;
    private Object listener;

    public Gradle35BuildOperationListenerAdapter(Gradle gradle, InvocationHandler invocationHandler) {
        this.gradle = (GradleInternal) gradle;
        register(invocationHandler);
    }

    private void register(InvocationHandler invocationHandler) {
        try {
            listener = Proxy.newProxyInstance(gradle.getClass().getClassLoader(), new Class[]{Class.forName("org.gradle.internal.progress.BuildOperationListener")}, invocationHandler);
            runBuildOperationServiceMethodForListener("addListener");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove() {
        runBuildOperationServiceMethodForListener("removeListener");
    }

    private void runBuildOperationServiceMethodForListener(String method) {
        try {
            Class<?> boServiceClass = Class.forName("org.gradle.internal.progress.BuildOperationService");
            Object buildOperationService = gradle.getServices().get(boServiceClass);
            buildOperationService.getClass().getMethod(method, Class.forName("org.gradle.internal.progress.BuildOperationListener")).invoke(buildOperationService, listener);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

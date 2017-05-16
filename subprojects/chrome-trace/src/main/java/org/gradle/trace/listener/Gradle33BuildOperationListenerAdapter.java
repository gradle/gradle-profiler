package org.gradle.trace.listener;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.ServiceRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class Gradle33BuildOperationListenerAdapter implements BuildOperationListenerAdapter {
    private GradleInternal gradle;
    private Object listener;

    public Gradle33BuildOperationListenerAdapter(Gradle gradle, InvocationHandler invocationHandler) {
        this.gradle = (GradleInternal) gradle;
        register(invocationHandler);
    }

    private void register(InvocationHandler invocationHandler) {
        try {
            listener = Proxy.newProxyInstance(gradle.getClass().getClassLoader(), new Class[]{Class.forName("org.gradle.internal.progress.InternalBuildListener")}, invocationHandler);
            getGlobalListenerManager().addListener(listener);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove() {
        try {
            getGlobalListenerManager().removeListener(listener);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private ListenerManager getGlobalListenerManager() throws ReflectiveOperationException {
        ServiceRegistry services = gradle.getServices();
        GradleLauncherFactory gradleLauncherFactory = services.get(GradleLauncherFactory.class);
        Field field = DefaultGradleLauncherFactory.class.getDeclaredField("globalListenerManager");
        field.setAccessible(true);
        return (ListenerManager) field.get(gradleLauncherFactory);
    }
}

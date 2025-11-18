package org.gradle.trace.util;

import java.lang.reflect.Method;

public interface ReflectionUtil {

    static Object invokerGetter(Object object, String methodName) {
        boolean originalAccessibility;
        Method method;
        try {
            method = object.getClass().getDeclaredMethod(methodName);
            originalAccessibility = method.isAccessible();
            method.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            return method.invoke(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            method.setAccessible(originalAccessibility);
        }
    }
}

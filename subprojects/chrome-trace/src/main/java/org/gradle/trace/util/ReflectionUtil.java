package org.gradle.trace.util;

import java.lang.reflect.Method;

public interface ReflectionUtil {

    static Object invokerGetter(Object object, String method) {
        try {
            Method getterMethod = object.getClass().getMethod(method);
            getterMethod.setAccessible(true);
            return getterMethod.invoke(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

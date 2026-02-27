package org.gradle.profiler.heapdump.agent;

/**
 * Utility class used by advices to get information from Gradle objects via reflection
 * This is in `heap-dump-runtime` as advices will be injected into Gradle's classloader and thus cannot reference classes from `heap-dump-agent`
 */
public class GradleReflectionUtils {

    public static String getProjectName(Object gradle) throws ReflectiveOperationException {
        if (gradle == null) {
            return null;
        }

        return gradle.getClass()
            .getMethod("getIdentityPath")
            .invoke(gradle)
            .toString();
    }

}

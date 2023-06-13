package org.gradle.profiler;

/**
 * This is a placeholder interface to detangle the implementation of profilers that only work with Gradle to
 * eventually work with other tools.
 */
public interface BuildConfiguration {
    default <T> T as(Class<? extends BuildConfiguration> clazz) {
        if (clazz.isInstance(this)) {
            return (T)this;
        }
        throw new IllegalArgumentException("Cannot cast " + this.getClass().getSimpleName() + " to a " + clazz.getSimpleName());
    }
}

package org.gradle.profiler.fixtures.file;

import groovy.lang.Closure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the test holds files open and therefore not to error if the test workspace can't be cleaned up.
 * <p>
 * An optional condition closure can be specified to control when cleanup errors are suppressed.
 * The closure receives the file that could not be deleted as both its delegate and argument,
 * allowing conditions like:
 * <pre>
 * {@literal @}LeaksFileHandles({ OperatingSystem.isWindows() && it.name.endsWith(".lock") })
 * </pre>
 *
 * @see TestNameTestDirectoryProvider
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
public @interface LeaksFileHandles {
    /**
     * Expect that the leakage might happen only under this condition.
     * The closure receives the file that could not be deleted as both its delegate and argument.
     *
     * @return the closure to evaluate, defaults to always true when not specified
     */
    Class<? extends Closure> value() default Closure.class;
}

package org.gradle.profiler.fixtures.file

import org.spockframework.runtime.extension.ExtensionAnnotation

import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Instructs Spock to use {@link CleanupTestDirectoryExtension} to clean up the test directory provided by a
 * {@link TestDirectoryProvider}.  This is to work around the fact that using
 * a test directory provider as a TestRule causes spock to swallow any test failures and the test directory
 * is cleaned up even for failed tests.  {@link CleanupTestDirectoryExtension} on the other hand, registers
 * an interceptor and listener which cleans up the test directory only when the test passes.
 * <p>
 * The annotation needs to know which field is the {@link TestDirectoryProvider} to clean up.
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtensionAnnotation(CleanupTestDirectoryExtension)
@interface CleanupTestDirectory {
    String value();
}

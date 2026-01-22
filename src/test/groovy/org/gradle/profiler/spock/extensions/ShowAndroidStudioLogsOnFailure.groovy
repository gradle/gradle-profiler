package org.gradle.profiler.spock.extensions

import org.spockframework.runtime.extension.ExtensionAnnotation

import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Instructs Spock to use {@link ShowAndroidStudioLogsOnFailureExtension} to show Android Studio logs on failure.
 * Log file is retrieved from a sandbox dir set on a test class. A field to retrieve sandbox dir can be set via
 * {@link ShowAndroidStudioLogsOnFailure#sandboxDirField()}.
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD])
@ExtensionAnnotation(ShowAndroidStudioLogsOnFailureExtension)
@interface ShowAndroidStudioLogsOnFailure {
    /**
     * A field name of the test class where Android Studio sandbox is set. Field has to be of type File.
     */
    String sandboxDirField() default "sandboxDir";
}

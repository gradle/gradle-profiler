package org.gradle.profiler.spock.extensions

import org.spockframework.runtime.AbstractRunListener
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.ErrorInfo
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

class ShowAndroidStudioLogsOnFailureExtension implements IAnnotationDrivenExtension<ShowAndroidStudioLogsOnFailure> {
    @Override
    void visitSpecAnnotation(ShowAndroidStudioLogsOnFailure annotation, SpecInfo spec) {
        spec.features.each { FeatureInfo feature ->
            feature.addIterationInterceptor(new FailureShowLogsInterceptor(annotation.sandboxDirField()))
        }
    }

    @Override
    void visitFeatureAnnotations(List<ShowAndroidStudioLogsOnFailure> annotations, FeatureInfo feature) {
        feature.addIterationInterceptor(new FailureShowLogsInterceptor(annotations[0].sandboxDirField()))
    }

    private static class FailureShowLogsInterceptor implements IMethodInterceptor {
        final String fieldName

        FailureShowLogsInterceptor(String fieldName) {
            this.fieldName = fieldName
        }

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            def errorListener = new AbstractRunListener() {
                @Override
                void error(ErrorInfo error) {
                    String message
                    def sandboxDir = invocation.instance.getProperties().get(fieldName)
                    if (sandboxDir == null) {
                        message = "Cannot read logs, sandbox dir set in field '$fieldName' is null."
                    } else if (!(sandboxDir instanceof File)) {
                        message = "Cannot read logs, sandbox dir set in field '$fieldName' is not of type ${File.class.getName()}, but is ${sandboxDir.class.getName()}."
                    } else {
                        File logFile = new File(sandboxDir, "/logs/idea.log")
                        message = logFile.exists()
                            ? "\n${logFile.text}"
                            : "Log file ${logFile} doesn't exist, nothing to print."
                    }
                    println("[ANDROID STUDIO LOGS] $message")
                }
            }
            invocation.spec.bottomSpec.addListener(errorListener)
            invocation.proceed()
        }
    }
}

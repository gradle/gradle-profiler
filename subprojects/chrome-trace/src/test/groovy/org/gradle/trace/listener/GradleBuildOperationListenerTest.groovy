package org.gradle.trace.listener

import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationIdentifier
import spock.lang.Specification

class GradleBuildOperationListenerTest extends Specification {
    def "normalizes windows file paths in display names #gradleVersion"() {
        given:
        def displayName = "Apply initialization script 'C:\\some\\location\\init.gradle' to build"
        def operation = BuildOperationDescriptor.displayName(displayName).build(
            new OperationIdentifier(42), new OperationIdentifier(1))

        expect:
        invocationHandler.getName(operation) ==
            "Apply initialization script 'C:/some/location/init.gradle' to build (42)"

        where:
        gradleVersion | invocationHandler
        "5.0"         | new Gradle50BuildOperationListenerInvocationHandler(null)
        "4.7"         | new Gradle47BuildOperationListenerInvocationHandler(null)
        "4.0"         | new Gradle40BuildOperationListenerInvocationHandler(null)
    }
}

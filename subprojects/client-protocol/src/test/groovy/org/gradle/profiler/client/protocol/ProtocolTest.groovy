package org.gradle.profiler.client.protocol

import org.gradle.profiler.client.protocol.messages.StudioAgentConnectionParameters
import org.gradle.profiler.client.protocol.messages.GradleInvocationCompleted
import org.gradle.profiler.client.protocol.messages.GradleInvocationParameters
import org.gradle.profiler.client.protocol.messages.GradleInvocationStarted
import spock.lang.Specification

import java.time.Duration

class ProtocolTest extends Specification {
    def "can send events between client and server"() {
        when:
        def server = new Server("some client")
        def timeout = Duration.ofSeconds(20)
        def client = new Client(server.port)
        def serverConnection = server.waitForIncoming(timeout)

        serverConnection.send(new StudioAgentConnectionParameters(new File("gradle-home")))
        def m1 = client.receiveConnectionParameters(timeout)

        client.send(new GradleInvocationStarted(1))
        def m2 = serverConnection.receiveGradleInvocationStarted(timeout)

        serverConnection.send(new GradleInvocationParameters(["gradle-arg"], ["jvm-arg"]))
        def m3 = client.receiveSyncParameters(timeout)

        client.send(new GradleInvocationCompleted(1, 123))
        def m4 = serverConnection.receiveGradleInvocationCompleted(timeout)

        then:
        m1.gradleInstallation.path == "gradle-home"
        m2.id == 1
        m3.gradleArgs == ["gradle-arg"]
        m3.jvmArgs == ["jvm-arg"]
        m4.id == 1
        m4.durationMillis == 123

        cleanup:
        client?.close()
        server?.close()
    }
}

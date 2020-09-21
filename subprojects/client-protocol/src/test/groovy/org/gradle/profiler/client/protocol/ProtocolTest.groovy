package org.gradle.profiler.client.protocol

import spock.lang.Specification

import java.time.Duration

class ProtocolTest extends Specification {
    def "can send events between client and server"() {
        when:
        def server = new Server("some client")
        def timeout = Duration.ofSeconds(20)
        def client = Client.INSTANCE
        client.connect(server.port)
        def serverConnection = server.waitForIncoming(timeout)

        serverConnection.send(new ConnectionParameters(new File("gradle-home")))
        def m1 = client.receiveConnectionParameters(timeout)

        client.send(new SyncStarted(1))
        def m2 = serverConnection.receiveSyncStarted(timeout)

        serverConnection.send(new SyncParameters(["gradle-arg"], ["jvm-arg"]))
        def m3 = client.receiveSyncParameters(timeout)

        client.send(new SyncCompleted(1, 123))
        def m4 = serverConnection.receiveSyncCompeted(timeout)

        then:
        m1.gradleInstallation.path == "gradle-home"
        m2.id == 1
        m3.gradleArgs == ["gradle-arg"]
        m3.jvmArgs == ["jvm-arg"]
        m4.id == 1
        m4.durationMillis == 123

        cleanup:
        client?.disconnect()
        server?.close()
    }
}

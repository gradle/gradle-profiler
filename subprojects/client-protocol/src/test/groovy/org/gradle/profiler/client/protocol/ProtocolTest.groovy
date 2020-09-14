package org.gradle.profiler.client.protocol

import spock.lang.Specification

import java.util.concurrent.LinkedBlockingQueue

class ProtocolTest extends Specification {
    def "client can sent events to server"() {
        when:
        def server = new Server("some client")
        def client = Client.INSTANCE
        client.connect(server.port)
        def connection = server.waitForIncoming()
        def messages = new LinkedBlockingQueue()
        connection.receive {
            messages.put(it)
        }

        client.send(new SyncStarted(1))
        client.send(new SyncCompleted(1, 123))

        then:
        def m1 = messages.take()
        m1 instanceof SyncStarted
        m1.id == 1
        def m2 = messages.take()
        m2 instanceof SyncCompleted
        m2.id == 1
        m2.durationMillis == 123

        cleanup:
        client?.disconnect()
        server?.close()
    }
}

package org.gradle.profiler.profilers.support

import spock.lang.Specification

class ProfilerConnectionTest extends Specification {
    ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.loopbackAddress)
    ProfilerConnection buildProcess
    ProfilerConnection profiler

    def setup() {
        buildProcess = new ProfilerConnection(new Socket(InetAddress.loopbackAddress, serverSocket.localPort))
        profiler = new ProfilerConnection(serverSocket.accept())
    }

    def cleanup() {
        buildProcess?.close()
        profiler?.close()
        serverSocket.close()
    }

    def "exchanges messages"() {
        when:
        buildProcess.send(new BuildFinished("1234"))
        def buildFinished = profiler.receive(BuildFinished)
        profiler.send(new RecordingStopped())
        def recordingStopped = buildProcess.receive(RecordingStopped)

        then:
        buildFinished.pid == "1234"
        recordingStopped != null
    }

    def "fails on unexpected message"() {
        when:
        buildProcess.send(new BuildFinished("1234"))
        profiler.receive(RecordingStopped)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Expected RecordingStopped but received BuildFinished(pid=1234)."
    }

    def "fails when the connection closes before a message"() {
        when:
        buildProcess.close()
        profiler.receive(BuildFinished)

        then:
        thrown(EOFException)
    }
}

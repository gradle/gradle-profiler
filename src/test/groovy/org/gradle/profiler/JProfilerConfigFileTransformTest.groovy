package org.gradle.profiler

import org.gradle.profiler.jprofiler.JProfilerConfig
import org.gradle.profiler.jprofiler.JProfilerConfigFileTransformer
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class JProfilerConfigFileTransformTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "transforms the JProfiler config file"() {

        setup:
        def sourceFile = tmpDir.newFile("source.xml")
        sourceFile << getClass().getResourceAsStream('/jprofiler/config/instrumentation.xml')
        assert sourceFile.text.length() > 0

        when:
        JProfilerConfig jProfilerConfig = new JProfilerConfig(null, null, null, null, true, true, false, ['builtin.JdbcProbe:+events+special', 'builtin.FileProbe'])
        def xml = new XmlSlurper().parse(JProfilerConfigFileTransformer.transform(sourceFile, '1', jProfilerConfig, "output/snapshot.jps", true))
        def triggers = xml.sessions.session[0].triggers
        def startActions = triggers.jvmStart.actions
        def stopActions = triggers.jvmStop.actions

        then:
        startActions.startRecording.cpu.@enabled == 'true'
        startActions.startRecording.allocation.@enabled == 'true'
        startActions.startMonitorRecording.size() == 1
        def jdbcProbe = startActions.startProbeRecording.findAll { it.@name == 'builtin.JdbcProbe' }
        jdbcProbe.size() == 1
        jdbcProbe.@events == 'true'
        jdbcProbe.@recordSpecial == 'true'
        def fileProbe = startActions.startProbeRecording.findAll { it.@name == 'builtin.FileProbe' }
        fileProbe.size() == 1
        fileProbe.@events == 'false'
        fileProbe.@recordSpecial == 'false'
        stopActions.saveSnapshot.@file == 'output/snapshot.jps'
    }
}

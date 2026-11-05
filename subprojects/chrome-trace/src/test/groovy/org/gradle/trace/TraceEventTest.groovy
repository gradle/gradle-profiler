package org.gradle.trace

import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/gradle/gradle-profiler/issues/541")
class TraceEventTest extends Specification {
    def "escapes strings in duration events"() {
        given:
        def event = new DurationEvent('Test "a"\nb\u0001', 'cat\\', 0, ['k"ey': 'C:\\dir\t'], null)

        expect:
        def json = event.toString()
        json.contains('"name": "Test \\"a\\"\\u000ab\\u0001"')
        json.contains('"cat": "cat\\\\"')
        json.contains('"args": {"k\\"ey": "C:\\\\dir\\u0009"}')
    }

    def "escapes strings in count events"() {
        given:
        def event = new CountEvent('he"ap', ['po"ol': 1d], null)

        expect:
        def json = event.toString()
        json.contains('"name": "he\\"ap"')
        json.contains('"args": {"po\\"ol": 1.0}')
    }
}

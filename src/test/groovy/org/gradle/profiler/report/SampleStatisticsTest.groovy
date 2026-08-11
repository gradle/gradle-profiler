package org.gradle.profiler.report

import spock.lang.Specification

class SampleStatisticsTest extends Specification {

    def "computes statistics of a sample"() {
        when:
        def stats = SampleStatistics.from([75, 70, 72, 68] as double[])

        then:
        stats.mean == 71.25d
        stats.min == 68.0d
        stats.p25 == 69.5d
        stats.median == 71.0d
        stats.p75 == 72.75d
        stats.max == 75.0d
        stats.stddev == Math.sqrt(6.6875d)
    }

    def "computes statistics of a single-value sample"() {
        when:
        def stats = SampleStatistics.from([42] as double[])

        then:
        stats.mean == 42.0d
        stats.min == 42.0d
        stats.p25 == 42.0d
        stats.median == 42.0d
        stats.p75 == 42.0d
        stats.max == 42.0d
        stats.stddev == 0.0d
    }

    def "rejects empty sample"() {
        when:
        SampleStatistics.from(new double[0])

        then:
        thrown(IllegalArgumentException)
    }

}

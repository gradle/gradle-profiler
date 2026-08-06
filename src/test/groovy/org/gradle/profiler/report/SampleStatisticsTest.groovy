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

    def "computes confidence of clearly different samples"() {
        expect:
        def confidence = SampleStatistics.confidenceOfDifference([75, 70, 72, 68] as double[], [85, 80] as double[])
        Math.abs(confidence - 0.9679612467744703d) < 1e-12
    }

    def "computes confidence of 0.5 for identical distributions"() {
        expect:
        SampleStatistics.confidenceOfDifference([1, 2, 3, 4] as double[], [1, 2, 3, 4] as double[]) == 0.5d
    }

    def "confidence handles ties using average ranks"() {
        expect:
        // Cross-checked against report.js (mann-whitney-utest + math.erf)
        def confidence = SampleStatistics.confidenceOfDifference([10, 10, 12, 14] as double[], [10, 12, 16, 18] as double[])
        Math.abs(confidence - 0.851258452792034d) < 1e-12
    }

    def "confidence is NaN when all values are equal"() {
        expect:
        Double.isNaN(SampleStatistics.confidenceOfDifference([5, 5] as double[], [5, 5] as double[]))
    }
}

package org.gradle.profiler.report;

import java.util.Arrays;

/**
 * Statistics over the measured iterations of a scenario.
 *
 * The computations intentionally match the ones performed by the HTML report (report.js),
 * so both reports show the same numbers: quantiles use linear interpolation (type R-7),
 * and the standard deviation is the population standard deviation.
 */
public class SampleStatistics {
    final double mean;
    final double min;
    final double p25;
    final double median;
    final double p75;
    final double max;
    final double stddev;

    private SampleStatistics(double mean, double min, double p25, double median, double p75, double max, double stddev) {
        this.mean = mean;
        this.min = min;
        this.p25 = p25;
        this.median = median;
        this.p75 = p75;
        this.max = max;
        this.stddev = stddev;
    }

    public static SampleStatistics from(double[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Cannot compute statistics of an empty sample");
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(values).average().getAsDouble();
        double stddev = Math.sqrt(Arrays.stream(values).map(value -> (value - mean) * (value - mean)).average().getAsDouble());
        return new SampleStatistics(
            mean,
            sorted[0],
            quantile(sorted, 0.25),
            quantile(sorted, 0.50),
            quantile(sorted, 0.75),
            sorted[sorted.length - 1],
            stddev
        );
    }

    private static double quantile(double[] sorted, double q) {
        double pos = (sorted.length - 1) * q;
        int base = (int) Math.floor(pos);
        double rest = pos - base;
        if (base + 1 < sorted.length) {
            return sorted[base] + rest * (sorted[base + 1] - sorted[base]);
        } else {
            return sorted[base];
        }
    }

}

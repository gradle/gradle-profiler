package org.gradle.profiler.report;

import org.apache.commons.math3.special.Erf;

import java.util.Arrays;

/**
 * Statistics over the measured iterations of a scenario.
 *
 * The computations intentionally match the ones performed by the HTML report (report.js),
 * so both reports show the same numbers: quantiles use linear interpolation (type R-7),
 * the standard deviation is the population standard deviation, and the confidence is
 * the normal CDF of the tie-corrected Mann-Whitney z statistic.
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

    /**
     * Returns the confidence that the two samples come from different distributions,
     * based on the normal approximation of the Mann-Whitney U test with tie correction.
     * Returns {@link Double#NaN} when the samples contain too little data to decide,
     * e.g. when all values are equal.
     */
    public static double confidenceOfDifference(double[] a, double[] b) {
        int n1 = a.length;
        int n2 = b.length;
        int n = n1 + n2;
        double[] all = new double[n];
        System.arraycopy(a, 0, all, 0, n1);
        System.arraycopy(b, 0, all, n1, n2);
        Arrays.sort(all);

        double rankSum = 0;
        for (double value : a) {
            rankSum += averageRank(all, value);
        }
        double u1 = rankSum - (n1 * (n1 + 1)) / 2.0;
        double u2 = (double) n1 * n2 - u1;
        double uMin = Math.min(u1, u2);
        double meanU = (double) n1 * n2 / 2;

        double tieCorrection = 0;
        for (int i = 0; i < n; ) {
            int tieCount = 1;
            while (i + tieCount < n && all[i + tieCount] == all[i]) {
                tieCount++;
            }
            if (tieCount > 1) {
                tieCorrection += (Math.pow(tieCount, 3) - tieCount) / ((double) n * (n - 1));
            }
            i += tieCount;
        }

        double stddev = Math.sqrt((double) n1 * n2 / 12 * ((n + 1) - tieCorrection));
        double z = Math.abs((uMin - meanU) / stddev);
        return 0.5 * (1 + Erf.erf(z / Math.sqrt(2)));
    }

    private static double averageRank(double[] sorted, double value) {
        int first = 0;
        while (sorted[first] != value) {
            first++;
        }
        int last = first;
        while (last + 1 < sorted.length && sorted[last + 1] == value) {
            last++;
        }
        // Ranks are 1-based, so the average rank of the equal values group is ((first + 1) + (last + 1)) / 2
        return (first + last) / 2.0 + 1;
    }
}

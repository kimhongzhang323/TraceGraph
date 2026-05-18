package io.tracegraph.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregate snapshot of an {@link EvalSuite} run. Holds counts, pass rate, latency percentiles,
 * and per-metric mean scores so callers can assert on suite-level health (e.g. "is the suite
 * healthier than last week").
 *
 * <p>Latency percentiles use the nearest-rank method on a sorted copy of {@link EvalResult#latencyMs}.
 * Mean score per metric ignores {@link Double#NaN} entries (used by skipped metrics) and preserves
 * the insertion order observed in the first result that contains each metric name.
 */
public record EvalSummary(
        int totalCases,
        int passedCases,
        int failedCases,
        double passRate,
        long latencyP50Ms,
        long latencyP95Ms,
        long latencyP99Ms,
        Map<String, Double> meanScoreByMetric) {

    public EvalSummary {
        meanScoreByMetric = Collections.unmodifiableMap(new LinkedHashMap<>(meanScoreByMetric));
    }

    public static EvalSummary from(List<? extends EvalResult<?>> results) {
        int total = results.size();
        if (total == 0) {
            return new EvalSummary(0, 0, 0, 0.0, 0L, 0L, 0L, Map.of());
        }

        int passed = 0;
        List<Long> latencies = new ArrayList<>(total);
        Map<String, double[]> sumsByMetric = new LinkedHashMap<>();
        for (EvalResult<?> result : results) {
            if (result.passed()) {
                passed++;
            }
            latencies.add(result.latencyMs());
            for (MetricScore score : result.scores()) {
                if (Double.isNaN(score.score())) {
                    continue;
                }
                double[] sumCount = sumsByMetric.computeIfAbsent(
                        score.metricName(), k -> new double[2]);
                sumCount[0] += score.score();
                sumCount[1] += 1.0;
            }
        }

        Collections.sort(latencies);
        long p50 = percentile(latencies, 0.50);
        long p95 = percentile(latencies, 0.95);
        long p99 = percentile(latencies, 0.99);

        Map<String, Double> means = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : sumsByMetric.entrySet()) {
            double[] sumCount = e.getValue();
            if (sumCount[1] > 0.0) {
                means.put(e.getKey(), sumCount[0] / sumCount[1]);
            }
        }

        double rate = (double) passed / total;
        return new EvalSummary(total, passed, total - passed, rate, p50, p95, p99, means);
    }

    private static long percentile(List<Long> sortedLatencies, double p) {
        int size = sortedLatencies.size();
        if (size == 0) {
            return 0L;
        }
        int rank = (int) Math.ceil(p * size);
        if (rank < 1) {
            rank = 1;
        }
        if (rank > size) {
            rank = size;
        }
        return sortedLatencies.get(rank - 1);
    }
}

package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BleuMetric<S> implements Metric<S> {

    private static final String NAME = "bleu";
    private static final int DEFAULT_MAX_NGRAM = 4;

    private final double passThreshold;
    private final int maxNgram;

    public BleuMetric(double passThreshold) {
        this(passThreshold, DEFAULT_MAX_NGRAM);
    }

    public BleuMetric(double passThreshold, int maxNgram) {
        if (passThreshold < 0.0 || passThreshold > 1.0) {
            throw new IllegalArgumentException("passThreshold must be in [0, 1]");
        }
        if (maxNgram < 1) {
            throw new IllegalArgumentException("maxNgram must be >= 1");
        }
        this.passThreshold = passThreshold;
        this.maxNgram = maxNgram;
    }

    public int maxNgram() {
        return maxNgram;
    }

    public double passThreshold() {
        return passThreshold;
    }

    @Override
    public MetricScore score(EvalCase<S> evalCase, S actual, long latencyMs) {
        List<String> reference = TextTokens.tokenize(String.valueOf(evalCase.expected()));
        List<String> candidate = TextTokens.tokenize(actual == null ? "" : actual.toString());

        double score = compute(reference, candidate);
        if (score >= passThreshold) {
            return MetricScore.pass(NAME, score);
        }
        return MetricScore.fail(NAME, score,
                "BLEU " + String.format("%.4f", score) + " below threshold " + passThreshold);
    }

    private double compute(List<String> reference, List<String> candidate) {
        if (reference.isEmpty() || candidate.isEmpty()) {
            return 0.0;
        }
        int effectiveMax = Math.min(maxNgram, candidate.size());
        double logSum = 0.0;
        for (int n = 1; n <= effectiveMax; n++) {
            double precision = modifiedPrecision(reference, candidate, n);
            if (precision == 0.0) {
                return 0.0;
            }
            logSum += Math.log(precision);
        }
        double geometricMean = Math.exp(logSum / effectiveMax);
        double brevityPenalty = candidate.size() >= reference.size()
                ? 1.0
                : Math.exp(1.0 - (double) reference.size() / candidate.size());
        return brevityPenalty * geometricMean;
    }

    private static double modifiedPrecision(List<String> reference, List<String> candidate, int n) {
        Map<List<String>, Integer> refCounts = ngramCounts(reference, n);
        Map<List<String>, Integer> candCounts = ngramCounts(candidate, n);
        int totalCandidate = 0;
        int clipped = 0;
        for (Map.Entry<List<String>, Integer> e : candCounts.entrySet()) {
            int candCount = e.getValue();
            totalCandidate += candCount;
            int refCount = refCounts.getOrDefault(e.getKey(), 0);
            clipped += Math.min(candCount, refCount);
        }
        return totalCandidate == 0 ? 0.0 : (double) clipped / totalCandidate;
    }

    private static Map<List<String>, Integer> ngramCounts(List<String> tokens, int n) {
        Map<List<String>, Integer> counts = new HashMap<>();
        if (tokens.size() < n) {
            return counts;
        }
        for (int i = 0; i <= tokens.size() - n; i++) {
            List<String> ngram = List.copyOf(tokens.subList(i, i + n));
            counts.merge(ngram, 1, Integer::sum);
        }
        return counts;
    }
}

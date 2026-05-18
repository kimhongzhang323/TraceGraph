package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;

import java.util.List;

public final class RougeMetric<S> implements Metric<S> {

    private static final String NAME = "rouge-l";

    private final double passThreshold;

    public RougeMetric(double passThreshold) {
        if (passThreshold < 0.0 || passThreshold > 1.0) {
            throw new IllegalArgumentException("passThreshold must be in [0, 1]");
        }
        this.passThreshold = passThreshold;
    }

    public double passThreshold() {
        return passThreshold;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public MetricScore score(EvalCase<S> evalCase, S actual, long latencyMs) {
        List<String> reference = TextTokens.tokenize(String.valueOf(evalCase.expected()));
        List<String> candidate = TextTokens.tokenize(actual == null ? "" : actual.toString());

        double score = fMeasure(reference, candidate);
        if (score >= passThreshold) {
            return MetricScore.pass(NAME, score);
        }
        return MetricScore.fail(NAME, score,
                "ROUGE-L " + String.format("%.4f", score) + " below threshold " + passThreshold);
    }

    private static double fMeasure(List<String> reference, List<String> candidate) {
        if (reference.isEmpty() || candidate.isEmpty()) {
            return 0.0;
        }
        int lcs = longestCommonSubsequence(reference, candidate);
        if (lcs == 0) {
            return 0.0;
        }
        double recall = (double) lcs / reference.size();
        double precision = (double) lcs / candidate.size();
        return 2.0 * precision * recall / (precision + recall);
    }

    private static int longestCommonSubsequence(List<String> a, List<String> b) {
        int m = a.size();
        int n = b.size();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.get(i - 1).equals(b.get(j - 1))) {
                    curr[j] = prev[j - 1] + 1;
                } else {
                    curr[j] = Math.max(prev[j], curr[j - 1]);
                }
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[n];
    }
}

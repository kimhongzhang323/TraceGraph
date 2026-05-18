package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TokenF1Metric<S> implements Metric<S> {

    private static final String NAME = "token-f1";

    private final double passThreshold;

    public TokenF1Metric(double passThreshold) {
        if (passThreshold < 0.0 || passThreshold > 1.0) {
            throw new IllegalArgumentException("passThreshold must be in [0, 1]");
        }
        this.passThreshold = passThreshold;
    }

    public double passThreshold() {
        return passThreshold;
    }

    @Override
    public MetricScore score(EvalCase<S> evalCase, S actual, long latencyMs) {
        List<String> reference = TextTokens.tokenize(String.valueOf(evalCase.expected()));
        List<String> candidate = TextTokens.tokenize(actual == null ? "" : actual.toString());

        double score = f1(reference, candidate);
        if (score >= passThreshold) {
            return MetricScore.pass(NAME, score);
        }
        return MetricScore.fail(NAME, score,
                "Token F1 " + String.format("%.4f", score) + " below threshold " + passThreshold);
    }

    private static double f1(List<String> reference, List<String> candidate) {
        if (reference.isEmpty() || candidate.isEmpty()) {
            return 0.0;
        }
        int shared = sharedMultisetSize(reference, candidate);
        if (shared == 0) {
            return 0.0;
        }
        double precision = (double) shared / candidate.size();
        double recall = (double) shared / reference.size();
        return 2.0 * precision * recall / (precision + recall);
    }

    private static int sharedMultisetSize(List<String> a, List<String> b) {
        Map<String, Integer> aCounts = new HashMap<>();
        for (String token : a) {
            aCounts.merge(token, 1, Integer::sum);
        }
        int shared = 0;
        Map<String, Integer> bCounts = new HashMap<>();
        for (String token : b) {
            bCounts.merge(token, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : bCounts.entrySet()) {
            shared += Math.min(e.getValue(), aCounts.getOrDefault(e.getKey(), 0));
        }
        return shared;
    }
}

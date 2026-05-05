package io.tracegraph.eval;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.eval.metric.Metric;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EvalSuite<S> {

    private final Graph<S> graph;
    private final List<EvalCase<S>> cases;
    private final List<Metric<S>> metrics;

    private EvalSuite(Graph<S> graph, List<EvalCase<S>> cases, List<Metric<S>> metrics) {
        this.graph = graph;
        this.cases = List.copyOf(cases);
        this.metrics = List.copyOf(metrics);
    }

    public static <S> Builder<S> builder(Graph<S> graph) {
        return new Builder<>(graph);
    }

    public List<EvalResult<S>> run() {
        List<EvalResult<S>> results = new ArrayList<>();
        for (EvalCase<S> evalCase : cases) {
            results.add(runCase(evalCase));
        }
        return List.copyOf(results);
    }

    private EvalResult<S> runCase(EvalCase<S> evalCase) {
        long start = System.currentTimeMillis();
        S actualOutput = null;
        List<MetricScore> scores;
        try {
            ExecutionResult<S> result = graph.run(evalCase.input());
            long latencyMs = System.currentTimeMillis() - start;
            actualOutput = result.finalState();
            scores = scoreAll(evalCase, actualOutput, latencyMs);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            scores = failAll(latencyMs);
        }
        boolean passed = scores.stream().allMatch(MetricScore::passed);
        return new EvalResult<>(evalCase, actualOutput, scores,
                System.currentTimeMillis() - start, passed);
    }

    private List<MetricScore> scoreAll(EvalCase<S> evalCase, S actual, long latencyMs) {
        List<MetricScore> scores = new ArrayList<>();
        for (Metric<S> metric : metrics) {
            scores.add(metric.score(evalCase, actual, latencyMs));
        }
        return scores;
    }

    private List<MetricScore> failAll(long latencyMs) {
        List<MetricScore> scores = new ArrayList<>();
        for (Metric<S> metric : metrics) {
            scores.add(metric.score(null, null, latencyMs));
        }
        return scores;
    }

    public static final class Builder<S> {

        private final Graph<S> graph;
        private final List<EvalCase<S>> cases = new ArrayList<>();
        private final List<Metric<S>> metrics = new ArrayList<>();

        private Builder(Graph<S> graph) {
            this.graph = Objects.requireNonNull(graph, "graph");
        }

        public Builder<S> addCase(EvalCase<S> evalCase) {
            cases.add(Objects.requireNonNull(evalCase, "evalCase"));
            return this;
        }

        public Builder<S> addCases(List<EvalCase<S>> evalCases) {
            evalCases.forEach(c -> cases.add(Objects.requireNonNull(c, "evalCase")));
            return this;
        }

        public Builder<S> metric(Metric<S> metric) {
            metrics.add(Objects.requireNonNull(metric, "metric"));
            return this;
        }

        public EvalSuite<S> build() {
            return new EvalSuite<>(graph, cases, metrics);
        }
    }
}

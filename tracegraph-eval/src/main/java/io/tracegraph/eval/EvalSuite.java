package io.tracegraph.eval;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.eval.metric.Metric;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EvalSuite<S> {

    private final Graph<S> graph;
    private final List<EvalCase<S>> cases;
    private final List<Metric<S>> metrics;
    private final boolean failFast;
    private final double minPassRate;

    private EvalSuite(Graph<S> graph, List<EvalCase<S>> cases, List<Metric<S>> metrics,
                      boolean failFast, double minPassRate) {
        this.graph = graph;
        this.cases = List.copyOf(cases);
        this.metrics = List.copyOf(metrics);
        this.failFast = failFast;
        this.minPassRate = minPassRate;
    }

    public static <S> Builder<S> builder(Graph<S> graph) {
        return new Builder<>(graph);
    }

    public List<EvalResult<S>> run() {
        List<EvalResult<S>> results = new ArrayList<>();
        for (EvalCase<S> evalCase : cases) {
            EvalResult<S> result = runCase(evalCase);
            results.add(result);
            if (failFast && !result.passed()) {
                break;
            }
        }
        return List.copyOf(results);
    }

    /**
     * Execute every {@link EvalCase} concurrently on a lazily-created virtual-thread-per-task
     * executor. The executor is shut down before this method returns. Results are returned in the
     * declaration order of the cases regardless of completion order. {@code failFast} is ignored
     * because all cases are submitted before any result is observed.
     *
     * @return per-case results in declaration order
     */
    public List<EvalResult<S>> runParallel() {
        if (cases.isEmpty()) {
            return List.of();
        }
        try (ExecutorService owned = Executors.newVirtualThreadPerTaskExecutor()) {
            return runOn(owned);
        }
    }

    /**
     * Execute every {@link EvalCase} concurrently on the supplied executor. The executor is NOT
     * shut down by this method — its lifecycle is the caller's responsibility. Results are returned
     * in the declaration order of the cases. {@code failFast} is ignored because all cases are
     * submitted before any result is observed.
     *
     * @param executor executor onto which each case is submitted
     * @return per-case results in declaration order
     */
    public List<EvalResult<S>> runParallel(ExecutorService executor) {
        Objects.requireNonNull(executor, "executor");
        if (cases.isEmpty()) {
            return List.of();
        }
        return runOn(executor);
    }

    private List<EvalResult<S>> runOn(ExecutorService executor) {
        List<CompletableFuture<EvalResult<S>>> futures = new ArrayList<>(cases.size());
        for (EvalCase<S> evalCase : cases) {
            futures.add(CompletableFuture.supplyAsync(() -> runCase(evalCase), executor));
        }
        List<EvalResult<S>> results = new ArrayList<>(futures.size());
        for (CompletableFuture<EvalResult<S>> future : futures) {
            results.add(future.join());
        }
        return List.copyOf(results);
    }

    public void assertPassed(List<? extends EvalResult<S>> results) {
        long total = results.size();
        if (total == 0) {
            return;
        }
        long passed = results.stream().filter(EvalResult::passed).count();
        double rate = (double) passed / total;
        if (rate < minPassRate) {
            String failedIds = results.stream()
                    .filter(r -> !r.passed())
                    .map(r -> r.evalCase().id())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            String message = String.format(
                    "Eval suite pass rate %.2f%% below threshold %.2f%% (failed: %s)",
                    rate * 100.0, minPassRate * 100.0, failedIds);
            throw new EvalAssertionException(message, results, rate, minPassRate);
        }
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
            if (!metric.canScore(evalCase)) {
                scores.add(MetricScore.skipped(metric.name()));
                continue;
            }
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
        private boolean failFast = false;
        private double minPassRate = 1.0;

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

        public Builder<S> failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public Builder<S> minPassRate(double minPassRate) {
            if (minPassRate < 0.0 || minPassRate > 1.0) {
                throw new IllegalArgumentException(
                        "minPassRate must be in [0.0, 1.0], got " + minPassRate);
            }
            this.minPassRate = minPassRate;
            return this;
        }

        public EvalSuite<S> build() {
            return new EvalSuite<>(graph, cases, metrics, failFast, minPassRate);
        }
    }
}

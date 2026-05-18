package io.tracegraph.eval;

import java.util.List;

public class EvalAssertionException extends RuntimeException {

    private final List<EvalResult<?>> results;
    private final List<EvalResult<?>> failedResults;
    private final double passRate;
    private final double minPassRate;

    public EvalAssertionException(String message,
                                  List<? extends EvalResult<?>> results,
                                  double passRate,
                                  double minPassRate) {
        super(message);
        List<EvalResult<?>> copied = new java.util.ArrayList<>(results.size());
        List<EvalResult<?>> failed = new java.util.ArrayList<>();
        for (EvalResult<?> r : results) {
            copied.add(r);
            if (!r.passed()) {
                failed.add(r);
            }
        }
        this.results = List.copyOf(copied);
        this.failedResults = List.copyOf(failed);
        this.passRate = passRate;
        this.minPassRate = minPassRate;
    }

    public List<EvalResult<?>> results() {
        return results;
    }

    public List<EvalResult<?>> failedResults() {
        return failedResults;
    }

    public double passRate() {
        return passRate;
    }

    public double minPassRate() {
        return minPassRate;
    }
}

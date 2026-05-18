package io.tracegraph.eval.baseline;

import io.tracegraph.eval.EvalResult;
import io.tracegraph.eval.MetricScore;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record EvalBaseline(Map<String, List<MetricScore>> scoresByCaseId, Instant createdAt) {

    public EvalBaseline {
        Objects.requireNonNull(scoresByCaseId, "scoresByCaseId");
        Objects.requireNonNull(createdAt, "createdAt");
        Map<String, List<MetricScore>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, List<MetricScore>> entry : scoresByCaseId.entrySet()) {
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        scoresByCaseId = Map.copyOf(copied);
    }

    public static EvalBaseline from(List<? extends EvalResult<?>> results) {
        Map<String, List<MetricScore>> map = new LinkedHashMap<>();
        for (EvalResult<?> result : results) {
            map.put(result.evalCase().id(), List.copyOf(result.scores()));
        }
        return new EvalBaseline(map, Instant.now());
    }

    public Optional<List<MetricScore>> scoresFor(String caseId) {
        return Optional.ofNullable(scoresByCaseId.get(caseId));
    }
}
